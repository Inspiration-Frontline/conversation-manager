# Conversation Manager Next Steps

Status: Round/Turn contract and PostgreSQL schema approved; core persistence and RPC implementation
are pending.

This document is the implementation queue for the finalized Round/Turn model. The source contracts
are:

- `round-turn-persistence-design.md`
- `../../docs/conversation-execution-persistence-relationships.md`
- `agent-breaker-protos/docs/conversation-manager-round-turn-contract.md`
- `agent-breaker-protos/docs/conversation-manager-implementation-handoff.md`
- `agent-breaker-protos/conversation-manager-protos/.../conversation_rpc_service.proto`

## Superseded Design

Do not implement the old message-oriented RPC proposal:

- `appendMessages`
- `saveConversationTurn`
- `createConversationIfAbsent`
- `touchConversation`
- `POST /conversation/messages`
- `POST /conversation/turn`

The new RPC model saves one complete Round atomically. It does not dual-write Round/Turn data into
the legacy `conversation_message` table. Existing HTTP conversation, message, group, sharing, and
fork behavior remains unchanged.

The finalized RPC surface is:

- `CreateConversation`
- `SaveConversationRound`
- `GetConversationRoundHistory`
- `GetConversationTurnHistory`
- `GetConversationReplay`
- `DeleteRounds`

The generated Dubbo Triple service is the new RPC boundary. Do not expand the handwritten legacy
`IConversationRpcService` to duplicate it.

## Current State

Completed:

- finalized Proto messages, RPC methods, business error codes, and response envelopes;
- normalized PostgreSQL tables for Conversation, Round, Turn, LLM calls, request messages, Tool
  definition snapshots, response Tool calls, and Tool executions;
- high-water storage in `conversation.latest_round_number`;
- Round tombstone columns and single-table database constraints;
- UTC timestamps and database-managed `modification_time`;
- application-managed logical relationships with no PostgreSQL foreign keys;
- Tool audit identity using `tool_key`, `tool_name`, `source_type`, and `definition_hash`.

Not implemented:

- MyBatis persistence for the new execution tables;
- cross-table and aggregate validation;
- canonical payload hashing and exact retry comparison;
- Round command/query services;
- replay reconstruction;
- conversation mutation locking;
- generated Dubbo Triple provider;
- end-to-end provider and consumer verification.

## Implementation Principles

### DAO Scope

Create one Mapper interface and one XML mapper per table only when an implementation path needs that
table. Keep methods table-scoped and query-specific; do not pre-build generic CRUD repositories.

Batch operations are appropriate only for repeated children that are already available as one
collection during `SaveConversationRound`, for example Turns, request messages, Tool definitions,
response Tool calls, and Tool executions. Use batch or set-based SQL when it materially simplifies
the aggregate write and preserves generated-ID linkage.

Do not add speculative APIs:

- Round needs a single insert, identity/retry lookup, history query, and tombstone update; it does
  not currently need bulk insert or bulk delete methods.
- LLM call is one row per Turn and does not need a general batch-delete API.
- Physical purge methods wait until a retention job is designed.
- Mapper methods that have no current service caller should not be added for symmetry.

Because the schema has no foreign keys, command services must validate parent existence and
cross-table ownership before writing. Cleanup code must explicitly delete children before parents.

### Layer Boundaries

- Mappers execute table-scoped SQL and return persistence entities.
- Validators enforce transport-independent domain rules.
- Command/query services own transactions, locks, high-water behavior, and aggregate assembly.
- The generated RPC provider converts Proto values and maps errors; it contains no persistence
  business logic.
- Existing HTTP services continue using trusted user context. New RPC operations use the explicit
  `user_id` after authenticating and authorizing the trusted caller.

## Phase 1: Required Persistence

Implement the minimum Mapper methods needed by the first command and query paths.

Initial write requirements:

- lock and load an owned active Conversation row;
- read and update `latest_round_number`;
- insert one Round;
- insert the Round's ordered child rows while preserving generated-ID relationships;
- find an existing active or tombstoned Round by `(conversation_id, round_number)`;
- tombstone one Round.

Initial read requirements:

- read active Rounds in `round_number` order;
- read Turns and each child collection in stored order;
- load raw payload columns only when explicitly requested;
- avoid one query per child row.

Add Mapper methods incrementally with their service use cases. Prefer a small number of bounded
batch queries over a generic aggregate Mapper or N+1 reads.

## Phase 2: Aggregate Validation

Implement a reusable validator independent of generated RPC transport classes. It must reject the
request before persistence when any invariant fails.

Validation areas:

- positive IDs, Round numbers, Turn numbers, and continuous ordering;
- Round, Turn, LLM call, and Tool execution time containment;
- completed, failed, and cancelled status consistency;
- text versus content-parts exclusivity and non-empty JSON arrays;
- Turn 1 `FULL_SNAPSHOT` and later `APPEND_DELTA` storage modes;
- one LLM call per Turn;
- request-message roles and historical Tool call linkage;
- unique `tool_key` and `tool_name` values inside one LLM request;
- valid `source_type` and lowercase SHA-256 `definition_hash`;
- response Tool call ID uniqueness;
- exact Tool mapping from frozen definition through response call to execution;
- exactly one execution outcome for every emitted Tool call;
- all logical parent IDs and same-Turn relationships, since the database has no foreign keys.

Keep structural/domain errors separate from infrastructure failures.

## Phase 3: Payload Hash And Retry Identity

Implement a versioned `ConversationRoundPayloadHasher`.

The canonical form must:

- include every persisted business field of `SaveConversationRoundRequest`;
- preserve repeated-field order;
- preserve Proto optional presence, including absent versus present-empty raw payloads;
- preserve JSON string bytes rather than reparsing or rewriting them;
- normalize enums to stable numeric values;
- exclude trusted caller metadata that is not persisted as Round content.

Store the hash version and lowercase SHA-256 digest on the Round.

Retry behavior:

- active row with equal hash: return the existing Round as an idempotent success;
- active row with different hash: `ROUND_NUMBER_CONFLICT`;
- tombstoned or retired number: `ROUND_NUMBER_RETIRED` before hash comparison;
- unsupported stored hash version: fail closed as an infrastructure/configuration error.

Add deterministic tests for field order, optional presence, nested Tool data, and raw-payload
retention differences.

## Phase 4: SaveConversationRound

Implement one transactional command service for the complete aggregate.

Transaction sequence:

1. Authenticate the trusted RPC caller and authorize the explicit `user_id`.
2. Run structural validation and compute the payload hash.
3. Acquire the conversation mutation lock.
4. Start a PostgreSQL transaction.
5. Select the owned active Conversation row `FOR UPDATE`.
6. Evaluate the requested Round number against `latest_round_number`.
7. Resolve new save, exact retry, conflicting retry, or retired number.
8. Insert the Round and required child rows.
9. Advance `latest_round_number` only for a new Round.
10. Commit and release the lock with owner verification.

No partial Round state is persisted in version `0.0.1`.

Implement `DeleteRounds` after the core save path:

- accept only a unique, contiguous active suffix ending at the latest active Round;
- validate the complete request before changing data;
- tombstone in descending order;
- retain child payloads and never decrease the high-water mark;
- use one new transaction per Round so the response can report committed partial progress;
- stop after the first failure and report lower unattempted values.

## Phase 5: History And Replay

Implement in this order:

1. `GetConversationRoundHistory`
2. `GetConversationTurnHistory`
3. `ROUND_SUMMARY` replay
4. `FULL_TURNS` replay
5. `MODEL_CONTEXT` replay

Query rules:

- omit tombstoned Rounds from normal history and replay;
- preserve Round, Turn, message, Tool definition, Tool call, and execution order;
- include raw request/response/result values only when requested and retained;
- reconstruct model context from the first `FULL_SNAPSHOT` and later `APPEND_DELTA` values;
- do not perform summarization, truncation, or token budgeting in conversation-manager;
- fail with `RESPONSE_TOO_LARGE` rather than silently truncating a unary response.

## Phase 6: Distributed Mutation Lock

Implement `ConversationMutationLock` with Redis:

- namespace: `conversation-manager:mutation:{conversation_id}`;
- unique ownership token;
- bounded acquisition wait;
- lease renewal for operations that may exceed one lease;
- atomic owner-checked release in `finally`.

The runner's long execution lock uses a different namespace. The conversation-manager lock remains
short and protects only authoritative mutations and their database transactions.

Before exposing the new RPC provider, route every existing conversation-scoped mutation through the
same mutation-lock abstraction. Acquire multiple Conversation locks in sorted ID order.

## Phase 7: Dubbo Triple RPC Provider

After the core services are independently tested:

1. Add the generated `conversation-manager-protos:0.0.1-SNAPSHOT` dependency.
2. Implement the generated Dubbo Triple provider in a dedicated adapter package.
3. Convert Proto requests to core commands and core results back to typed Proto `data`.
4. Keep every business response at exactly `base = 1` and `data = 2`.
5. Use gRPC status for transport/infrastructure failures and typed business codes for domain
   outcomes.
6. Do not require the HTTP `UserContextService` ThreadLocal in RPC execution.

The first provider milestone contains:

- `CreateConversation`
- `SaveConversationRound`
- `GetConversationRoundHistory`
- `GetConversationTurnHistory`

Replay and tail deletion may follow once this milestone is verified.

## Verification Gates

Each phase adds focused unit and PostgreSQL integration tests. Before agent-runner integration,
verify the complete provider with real PostgreSQL, Redis, Nacos, Dubbo Triple provider, and a
generated client.

Required scenarios:

- completed, failed, and cancelled Rounds, including valid zero-Turn failure;
- invalid ordering, status, time, content, and Tool linkage;
- concurrent attempts to assign the same next Round number;
- exact retry, conflicting retry, and delayed retry after tombstone;
- multi-Turn snapshot-plus-delta reconstruction;
- parallel Tool calls and failed/cancelled Tool executions;
- raw payload include, exclude, and retained-empty presence;
- active-history filtering and replay boundaries;
- valid tail deletion, invalid non-tail deletion, and partial deletion failure;
- response-size guard behavior;
- ownership-safe not-found behavior.

Stop every process started by integration verification and clean all test data.

## Deferred Work

- in-progress Round checkpoints and crash recovery;
- streaming or paginated history when unary responses approach limits;
- Tool and MCP implementation versioning and execution-level replay;
- physical retention, purge, and anonymization jobs;
- final agent-configuration-center Agent Definition integration;
- agent-runner's long execution lock and end-to-end client integration;
- product-specific raw-payload redaction policy.

The immediate next task is Phase 1 persistence plus the Phase 2 validator interfaces needed by
`SaveConversationRound`. Do not start the RPC provider before those core boundaries are tested.
