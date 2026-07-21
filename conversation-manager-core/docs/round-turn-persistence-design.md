# Round/Turn Persistence Design Review

Status: schema implemented in the initial forward/rollback SQL; persistence services and RPC are pending

This document defines the PostgreSQL persistence model and transaction boundaries required by the
finalized `ConversationRpcService` Round/Turn contract. The table model is now represented in the
initial forward/rollback SQL and PostgreSQL entities. RPC provider implementation still follows the
table-scoped Mapper and persistence-service work described below.

The source contract is maintained in the sibling `agent-breaker-protos` repository:

- `docs/conversation-manager-implementation-handoff.md`
- `docs/conversation-manager-round-turn-contract.md`
- `conversation-manager-protos/.../conversation_rpc_service.proto`

## Review Decision

The recommended model is a normalized, append-oriented execution model stored beside the existing
conversation-management tables.

- Keep `conversation`, `conversation_message`, grouping, sharing, and file tables intact.
- Add `latest_round_number` to `conversation` as the authoritative high-water mark.
- Add dedicated Round, Turn, LLM call, request-message, tool-definition, tool-call, and tool-execution
  tables.
- Make the new tables the only persistence source for the finalized Round/Turn RPC surface.
- Do not dual-write new RPC data into `conversation_message`.
- Preserve logically deleted Round rows and all children until a separate retention job purges or
  anonymizes their payloads.

This separation is required because `conversation_message` cannot represent atomic rounds, exact
retry identity, snapshot-plus-delta storage, per-turn agent identity, tool execution status, raw
payload presence, replay boundaries, or non-reusable round numbers.

## Scope

This design covers:

- `CreateConversation`
- `SaveConversationRound`
- Round and Turn history
- Replay at all three detail levels
- Logical tail deletion
- High-water and idempotency behavior
- Raw-payload retention
- Conversation-manager's short mutation lock

This design does not cover:

- Agent-runner's long execution lock implementation
- In-progress Round checkpoints
- Full Agent Definition loading
- Streaming or paginated history APIs
- A new sharing/forking model for Round/Turn data
- Physical retention and anonymization policy details

## Existing Model Compatibility

The existing tables remain responsible for current HTTP behavior:

| Existing table | Existing responsibility | Decision |
| --- | --- | --- |
| `conversation` | Ownership, title, pinning, soft deletion, list ordering | Extend with high-water mark |
| `conversation_message` | Legacy flat message history | Preserve; no new RPC dual-write |
| `conversation_group*` | Grouping and root-list behavior | Preserve |
| `conversation_sharing` | Round snapshot through `end_round_number` | Use normalized Round high-water mark |
| `message_file` | Uploaded file metadata | Preserve |

The first RPC provider milestone preserved legacy HTTP response shapes. Phase 8 later moved sharing
and Fork to normalized Round/Turn execution records and removed the legacy message boundary from
`conversation_sharing`. The `conversation_message` table remains only for its separate legacy HTTP
history/export surface and is not dual-written by the current Agent execution path.

## Aggregate Layout

```text
conversation
  latest_round_number
  |
  +-- conversation_round
        |
        +-- conversation_turn
              |
              +-- conversation_llm_call
              |     |
              |     +-- conversation_llm_request_message
              |     |     |
              |     |     +-- conversation_llm_request_message_tool_call
              |     |
              |     +-- conversation_llm_tool_definition
              |     |
              |     +-- conversation_llm_response_tool_call
              |
              +-- conversation_tool_call_execution
```

Content-part arrays remain one ordered JSONB value. They are structured value objects, not
independently addressed entities. Provider-supplied JSON strings such as tool arguments,
`parameters_json`, response format, and raw payloads remain TEXT so their exact bytes and optional
presence are preserved.

## Conversation Extension

Add one column to `conversation`:

| Column | Type | Rules |
| --- | --- | --- |
| `latest_round_number` | `BIGINT` | `NOT NULL DEFAULT 0`, check `>= 0` |

This value is a high-water mark, not the maximum active Round. It advances only when a new Round is
successfully saved. Logical deletion never decreases it.

The `Conversation` entity and `ConversationMapper` result maps must include the new column. Existing
conversation list queries do not need to order by it.

## New Tables

All new tables use `BIGSERIAL` internal primary keys and the same creator/modifier/time audit fields
as `EntityBase`. Runtime rows are normally append-only, so creation and modification time initially
match; database triggers keep modification time correct for retention or tombstone updates.
Ownership is authorized through the parent `conversation` row. Cross-table relationships are
validated by conversation-manager services rather than PostgreSQL foreign keys.

### `conversation_round`

One row represents one complete saved Round or its tombstone.

| Column | Type | Rules |
| --- | --- | --- |
| `id` | `BIGSERIAL` | Primary key |
| `conversation_id` | `VARCHAR(64)` | Logical reference to `conversation.conversation_id` |
| `round_number` | `BIGINT` | Positive, unique inside conversation |
| `user_request_content` | `TEXT` | Text-only request representation |
| `user_request_content_parts` | `JSONB` | Multimodal request representation |
| `final_answer_content` | `TEXT` | Completed Round text answer |
| `final_answer_content_parts` | `JSONB` | Completed Round multimodal answer |
| `final_source_turn_number` | `BIGINT` | Completed Round source Turn |
| `status` | `VARCHAR(16)` | `COMPLETED`, `FAILED`, or `CANCELLED` |
| `error_message` | `TEXT` | Empty for completed, non-empty for failed |
| `start_time` | `TIMESTAMPTZ` | UTC instant converted from caller epoch milliseconds |
| `end_time` | `TIMESTAMPTZ` | UTC instant, not earlier than start |
| `payload_hash_version` | `SMALLINT` | Canonical hash algorithm version |
| `payload_hash` | `CHAR(64)` | SHA-256 of the canonical persisted request |
| `deleted` | `BOOLEAN` | Logical deletion flag |
| `deletion_time` | `TIMESTAMPTZ` | Set only for a tombstone |
| `deleted_by` | `BIGINT` | Authorized user represented by the RPC |
| `creation_time` | `TIMESTAMPTZ` | Database default `NOW()` |
| `modification_time` | `TIMESTAMPTZ` | Database default and update trigger |

#### User Request Content Representation

`user_request_content` and `user_request_content_parts` are two mutually exclusive representations
of the same user request. They are not duplicate copies and must never be populated together.

- `user_request_content` stores a text-only request. It is used when the request contains no
  structured content parts. In this form, `user_request_content_parts` is `NULL`.
- `user_request_content_parts` stores a non-empty ordered JSONB array for a structured or multimodal
  request, including requests with attachments. In this form, `user_request_content` is `NULL`.

For example, a text-only request is stored as:

```text
user_request_content = "Explain the retry policy"
user_request_content_parts = NULL
```

A request containing visible text and file references is stored conceptually as:

```json
[
  { "type": "text", "text": "Compare these files" },
  {
    "type": "file",
    "file_url": { "url": "agentbreaker-file://file_001" },
    "filename": "design.md"
  }
]
```

and the corresponding scalar column remains `NULL`. Content parts contain stable file references
and presentation metadata, not file bytes or expiring OSS URLs. File bytes and extracted evidence
remain owned by the file-resource subsystem.

The visible text inside a structured request may be projected by the service for HTTP history and
automatic title derivation, but that projection is not written back into `user_request_content`.
Keeping one canonical persisted representation prevents scalar text and structured JSON from
drifting apart. PostgreSQL constraint `ck_round_user_request` enforces this rule: either non-blank
scalar text is present with no parts, or a non-empty JSON array is present with no scalar text.

Checks enforce:

- Exactly one user-request content representation is present and non-empty.
- A completed Round has exactly one final-answer representation, a positive source Turn, and an
  empty error.
- A failed or cancelled Round has no final answer or source Turn.
- A failed Round has a non-empty error; a cancelled Round may have an empty error.
- Deletion metadata is either all absent for active rows or all populated for tombstones.
- Content-parts values are non-empty JSON arrays when present.

The save service validates the source-Turn relationship before commit:

```text
(id, final_source_turn_number)
  -> conversation_turn(round_id, turn_number)
```

### `conversation_turn`

| Column | Type | Rules |
| --- | --- | --- |
| `id` | `BIGSERIAL` | Primary key |
| `round_id` | `BIGINT` | Logical reference to `conversation_round.id` |
| `turn_number` | `BIGINT` | Positive and unique inside Round |
| `agent_id` | `BIGINT` | Positive stable database identity |
| `agent_name` | `VARCHAR(200)` | Non-empty runtime/handoff name |
| `agent_version` | `INTEGER` | Positive resolved definition version |
| `status` | `VARCHAR(16)` | Completed, failed, or cancelled |
| `error_message` | `TEXT` | Status-consistent error value |
| `start_time` | `TIMESTAMPTZ` | UTC instant converted from caller epoch milliseconds |
| `end_time` | `TIMESTAMPTZ` | UTC instant, not earlier than start |
| `creation_time` | `TIMESTAMPTZ` | Database default `NOW()` |

The service validates a continuous ordered sequence from 1 through N. The unique constraint on
`(round_id, turn_number)` is the database defense against duplicates.

### `conversation_llm_call`

There is exactly one LLM call row per Turn.

Request columns:

- `turn_id` as a unique logical reference to `conversation_turn`
- `provider`, `model`, `request_id`, and `trace_id`
- `message_storage_mode`
- `tool_choice_present`, `tool_choice_mode`, and `tool_choice_name`
- `response_format` as TEXT
- nullable `temperature` and `max_output_tokens`
- nullable `raw_request`; NULL means absent and empty TEXT means retained empty payload
- `start_time` and `end_time` as UTC `TIMESTAMPTZ`

Response columns:

- `response_message_present`
- `response_content` or `response_content_parts`
- `finish_reason`
- `usage_present` plus prompt, completion, total, cached-prompt, and reasoning token counts
- nullable `raw_response` with Proto-presence semantics
- `response_error_message`
- nullable normalized `reasoning_content`, separate from user-visible response content

Checks enforce request storage mode, optional tool-choice consistency, non-negative token values,
content representation exclusivity, and response success/error consistency. Time containment and
other parent/child rules remain service validations because PostgreSQL `CHECK` constraints cannot
reference another table.

### `conversation_llm_request_message`

Stores the ordered `LlmRequest.messages` array.

- Unique `(llm_call_id, message_order)` preserves order.
- `role` stores the normalized Proto role.
- `content` and `content_parts` are mutually exclusive, but both may be absent for a tool-call-only
  assistant message.
- `tool_call_id` is allowed only for a TOOL role.
- `content_parts` is JSONB and must be an array when present.

### `conversation_llm_request_message_tool_call`

Stores historical tool calls embedded in request-context assistant messages.

- Logical reference to `conversation_llm_request_message`.
- Unique `(request_message_id, call_order)`.
- Unique `(request_message_id, tool_call_id)`.
- Stores `tool_call_id`, `type`, `function_name`, and exact `arguments` TEXT.

This table is separate from current-response calls because request messages may contain tool calls
from earlier model invocations and must replay exactly as request context.

### `conversation_llm_tool_definition`

Stores the complete ordered tool definition list offered in one model request.

- Unique `(llm_call_id, tool_order)`.
- Unique `(llm_call_id, tool_key)` prevents duplicate logical Tools.
- Unique `(llm_call_id, tool_name)` enforces provider-visible name uniqueness.
- Stores the globally stable `tool_key`, provider-facing `tool_name`, execution provenance
  `source_type`, description, exact `parameters_json` TEXT, non-null `strict`, and a lowercase
  SHA-256 `definition_hash`.
- `source_type` is restricted to `INTERNAL`, `BUSINESS`, or `MCP`. Provider adapters derive
  protocol-specific wrappers such as OpenAI's `function`; that wrapper is not stored as provenance.

### `conversation_llm_response_tool_call`

Stores tool calls emitted by the current LLM response.

- Includes `turn_id` and `llm_call_id` so the service can validate same-Turn integrity.
- Unique `(llm_call_id, call_order)`.
- Unique `(llm_call_id, tool_call_id)`.
- Stores `tool_call_id`, `type`, `function_name`, and exact `arguments` TEXT.

### `conversation_tool_call_execution`

Stores exactly one execution outcome for a current-response tool call.

- Stores logical references to `conversation_turn` and the response Tool call.
- The save service ensures the execution and response Tool call belong to the same Turn.
- Unique `response_tool_call_id` prevents more than one execution for a model-emitted call.
- Unique `(turn_id, execution_order)` preserves parallel-call reporting order.
- Stores the globally stable `tool_key`, status, normalized result content or content-parts,
  optional raw result, error message, and UTC start/end timestamps.

The database can enforce at most one execution per response-call ID inside this table. The service
must validate that the referenced response call exists, belongs to the same Turn, and has exactly
one execution, including failed and cancelled executions.

## Indexes

Required indexes are intentionally read-path driven:

| Table | Index | Purpose |
| --- | --- | --- |
| `conversation_round` | unique `(conversation_id, round_number)` | Idempotency and no reuse |
| `conversation_round` | `(conversation_id, round_number) WHERE deleted = FALSE` | Active history/replay |
| `conversation_round` | `(conversation_id, deleted, end_time)` | Cleanup/analysis support |
| `conversation_turn` | unique `(round_id, turn_number)` | Ordered Turn lookup |
| `conversation_llm_call` | unique `(turn_id)` | One LLM call per Turn |
| Request message | unique `(llm_call_id, message_order)` | Ordered replay |
| Request tool call | unique `(request_message_id, call_order)` | Ordered replay |
| Tool definition | unique `(llm_call_id, tool_order)` | Ordered request reconstruction |
| Response tool call | unique `(llm_call_id, call_order)` | Ordered response reconstruction |
| Tool execution | unique `(turn_id, execution_order)` | Ordered execution reconstruction |
| Tool execution | unique `(response_tool_call_id)` | Exactly-at-most-one execution |

Every logical parent-reference column used for joins is covered by either an explicit B-tree index
or the B-tree index PostgreSQL creates for a leading UNIQUE constraint. Do not add a second ordinary
index when the same leading columns are already covered. Raw payloads, JSON content, errors, and
hashes do not receive general-purpose indexes.

## Canonical Payload Hash

`SaveConversationRound` needs an efficient exact-retry check that remains valid after raw payloads
are purged. The Round row therefore stores a versioned SHA-256 hash of a canonical representation
of every business field in `SaveConversationRoundRequest`, excluding authenticated caller metadata
that is not persisted as Round content.

Canonicalization rules must be implemented once in conversation-manager core:

- Preserve repeated-field order.
- Preserve Proto optional presence, including absent versus present-empty raw payloads.
- Normalize enum values to their numeric Proto values.
- Preserve string bytes; do not parse or rewrite string fields containing JSON.
- Include `conversation_id`, `round_number`, all Round fields, all Turns, and all nested values.
- Version the algorithm before changing any rule.

New Rounds use the current hash version. Retry handling reads the stored version and computes with
that version's canonicalizer; supported historical canonicalizers must remain available while
Rounds using them can still receive retries. The hash is the retry identity. For an active existing
Round:

- Equal hash under the stored version: idempotent retry; return the persisted Round.
- Different hash: `ROUND_NUMBER_CONFLICT`.
- Unsupported stored version: fail closed as an infrastructure/configuration error; never classify
  an uncomputed comparison as an idempotent retry.

For a tombstoned Round, always return `ROUND_NUMBER_RETIRED` before comparing the hash. Hashes are
retained when raw payloads are purged.

## Save Transaction

The save sequence is:

1. Authenticate the trusted RPC caller and authorize it to act for `user_id`.
2. Perform transport-independent structural validation before acquiring a lock.
3. Compute the canonical payload hash.
4. Acquire Redis lock `conversation-manager:mutation:{conversation_id}` with an ownership token,
   bounded wait, lease renewal, and owner-checked release.
5. Start one PostgreSQL transaction.
6. Select the owned, active conversation row `FOR UPDATE`.
7. Evaluate `round_number` against `latest_round_number`:
   - Equal to high-water + 1: continue as a new Round.
   - At or below high-water with active row: compare hash for retry/conflict.
   - At or below high-water with tombstone or no row: retired.
   - Above high-water + 1: invalid request because it introduces a gap.
8. Insert Round and all normalized children with table-scoped batch Mapper methods.
9. Update `conversation.latest_round_number`, `modifier_id`, and therefore trigger
   `modification_time` in the same transaction.
10. Commit, then release the Redis lock in `finally`.

The Redis lock coordinates pods. `SELECT ... FOR UPDATE`, the unique Round key, and the high-water
update are still required as database-level defenses. A lock timeout is an infrastructure failure,
not a successful business response.

## Create Transaction And Empty Cleanup

`CreateConversation` remains intentionally non-idempotent.

1. Authenticate and authorize `user_id`.
2. Apply a Redis-backed distributed per-user admission limit with default two create requests per
   second. An admitted request consumes its slot even if the later database insert fails.
3. Insert a normal `conversation` row with `latest_round_number = 0` in one transaction.

The later empty-conversation cleanup job may soft-delete a conversation only when all conditions
remain true while holding the conversation mutation lock:

- Creation time is older than the configured grace period.
- The conversation is not already deleted.
- No `conversation_round` row exists, including tombstones.
- No legacy `conversation_message` row exists.
- The grace period exceeds the maximum runner execution and retry window.

The job must recheck these predicates inside its deletion transaction after acquiring the lock.

## Tail Deletion Transactions

Partial deletion is part of the RPC contract, so `DeleteRounds` is not one all-or-nothing database
transaction.

1. Acquire and retain the conversation mutation lock for the whole operation.
2. In a read transaction, lock the conversation row and validate that all requested values are
   unique, positive, contiguous active Round numbers ending at the latest active Round.
3. Sort descending.
4. Tombstone each Round in a separate `REQUIRES_NEW` transaction implemented by a separate
   Spring bean so transaction proxying is effective.
5. After the first failed deletion, stop and report that Round plus all lower unattempted values.
6. Never change `latest_round_number`.

Each tombstone update sets `deleted`, `deletion_time`, `deleted_by`, and the Round modification
timestamp. Child data remains available only to separately authorized retention/analysis paths.

Before the new RPC provider is exposed, every existing conversation-scoped mutation must use the
same mutation-lock namespace. This includes title updates, conversation deletion, message deletion,
pin/group membership changes, and any sharing operation that captures a mutable boundary. Bulk
operations must acquire conversation locks in sorted ID order to avoid deadlocks. This lock rollout
belongs in the implementation phase and must preserve current HTTP behavior.

## Replay Algorithms

### Round summary

Read active Round rows through the inclusive boundary, ordered by `round_number`. Do not join Turn
or raw-payload tables.

### Full Turns

Read active Round and Turn aggregates in ascending Round/Turn order. Load ordered child collections
with bounded batch queries, not one query per row. Apply `include_raw_payloads` while mapping so
raw fields stay absent when not requested.

### Model context

For a boundary containing Turns:

1. Start from Turn 1's required `FULL_SNAPSHOT` request messages.
2. Append each later Turn's `APPEND_DELTA` request messages through the selected Turn.
3. Append only the selected Turn's response assistant message and tool execution results. Earlier
   responses/results must already be represented in the next Turn's request delta.
4. Do not append `final_answer` separately; it points to an existing Turn response.

For a failed or cancelled zero-Turn boundary, return the model context reconstructed through the
nearest preceding active executed Turn. The zero-Turn user request is visible in Round history but
was never sent to a model and is not synthesized into model context.

Conversation-manager performs no semantic summarization, truncation, or token budgeting.

## Raw Payload Retention

- Store `raw_request`, `raw_response`, and `raw_result` as nullable TEXT.
- Rely on PostgreSQL TOAST for initial storage compression.
- Never index raw fields.
- A retention job may set raw fields to NULL without deleting normalized logical data.
- Purging raw data must not change Round activity, high-water values, or payload hashes.
- Application and runner layers must redact secrets before persistence.
- Authorized reads must opt in to raw fields; normal queries must not select them unnecessarily.

## Response Size Guard

History and replay are unary in the current contract. Build responses through a configured byte
budget lower than the Dubbo Triple/gRPC transport limit. If the result would exceed that budget,
return `RESPONSE_TOO_LARGE`; do not silently truncate or move the replay boundary.

## Mapper And Service Boundaries

Create one Mapper interface and XML file per new primary table. Do not create a single aggregate
Mapper containing unrelated CRUD.

Recommended service split:

- `ConversationRoundCommandService`: create/save validation and transaction orchestration.
- `ConversationRoundService`: Round persistence, history, and replay reads.
- `ConversationRoundDeletionService`: lock-held suffix orchestration.
- `ConversationRoundDeletionTransaction`: one `REQUIRES_NEW` tombstone operation.
- `ConversationMutationLock`: Redis token/lease lifecycle.
- `ConversationRoundValidator`: transport-independent cross-field validation.
- `ConversationRoundPayloadHasher`: versioned canonical hashing.
- Dedicated Proto adapter/provider: generated DTO conversion and response envelopes only.

Existing `ConversationService` and the handwritten `IConversationRpcService` remain untouched in
the first implementation pass.

## Implemented Migration Plan

The current early-development schema applies these changes directly in the initial forward SQL and
its matching rollback SQL.

Forward order:

1. Add `conversation.latest_round_number` with default and check.
2. Create Round and Turn tables.
3. Create LLM and tool child tables.
4. Add single-table checks, uniqueness constraints, and indexes.
5. Add modification-time triggers.

Rollback order is the exact reverse. It drops all tables created by this clean-schema initialization
and then drops the shared modification-time function.

The migration is safe for existing conversations because their high-water mark starts at zero and
no new Round rows exist.

## Verification Gates

Before the schema is accepted for RPC implementation, verify:

- Forward migration on a database containing current legacy data.
- Rollback restores that legacy schema and data unchanged.
- Unique and check constraints reject invalid identities, times, statuses, content shapes, and
  duplicate ordering keys.
- Concurrent saves cannot advance the same conversation high-water twice.
- Exact retry, conflicting retry, and delayed retry after tombstone return the specified codes.
- Raw purge leaves normalized replay and retry identity intact.
- Round summary avoids loading child/raw data.
- Model-context replay handles multiple Turns and a zero-Turn failed Round deterministically.
- Tail deletion commits prior successes, stops after the first failure, and preserves high-water.
- Existing HTTP list, pin, group, share, fork, export, and legacy message history behavior remains
  unchanged.

## Approval Checklist

This design recommends approval of the following decisions before migration work begins:

- New normalized tables, with no `conversation_message` dual-write.
- `conversation.latest_round_number` as the only authoritative high-water mark.
- Round tombstones retained in the Round table.
- Versioned canonical SHA-256 hash as retry identity.
- JSONB only for structured content-part arrays; provider JSON strings remain TEXT.
- Separate per-Round deletion transactions while retaining one distributed mutation lock.
- Legacy sharing/forking does not include new execution history in the first milestone.

The next implementation task is the table-scoped Mapper and persistence layer. RPC adapter code
still comes afterward.
