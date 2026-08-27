CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE "conversation_turn"
    ADD COLUMN IF NOT EXISTS "llm_start_time" TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS "llm_end_time" TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS "request_id" VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS "trace_id" VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS "message_storage_mode" VARCHAR(32),
    ADD COLUMN IF NOT EXISTS "request_messages_snapshot" JSONB,
    ADD COLUMN IF NOT EXISTS "request_messages_snapshot_hash" CHAR(64),
    ADD COLUMN IF NOT EXISTS "raw_request" TEXT,
    ADD COLUMN IF NOT EXISTS "response_message_present" BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS "response_content" TEXT,
    ADD COLUMN IF NOT EXISTS "response_content_parts" JSONB,
    ADD COLUMN IF NOT EXISTS "finish_reason" VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS "usage_present" BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS "prompt_tokens" BIGINT,
    ADD COLUMN IF NOT EXISTS "completion_tokens" BIGINT,
    ADD COLUMN IF NOT EXISTS "total_tokens" BIGINT,
    ADD COLUMN IF NOT EXISTS "cached_prompt_tokens" BIGINT,
    ADD COLUMN IF NOT EXISTS "reasoning_tokens" BIGINT,
    ADD COLUMN IF NOT EXISTS "raw_response" TEXT,
    ADD COLUMN IF NOT EXISTS "response_error_message" TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS "reasoning_content" TEXT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM "conversation_llm_call" call
        LEFT JOIN "conversation_turn" turn ON turn."id" = call."turn_id"
        WHERE turn."id" IS NULL
    ) THEN
        RAISE EXCEPTION 'Round/Turn consolidation stopped: an LLM call has no Turn.';
    END IF;
END;
$$;

UPDATE "conversation_turn" turn
SET "llm_start_time" = call."start_time",
    "llm_end_time" = call."end_time",
    "request_id" = call."request_id",
    "trace_id" = call."trace_id",
    "message_storage_mode" = call."message_storage_mode",
    "request_messages_snapshot" = COALESCE((
        SELECT JSONB_AGG(
            JSONB_BUILD_OBJECT(
                'role', message."role",
                'content', message."content",
                'content_parts', message."content_parts",
                'tool_call_id', message."tool_call_id",
                'tool_calls', COALESCE((
                    SELECT JSONB_AGG(
                        JSONB_BUILD_OBJECT(
                            'id', historical_call."tool_call_id",
                            'type', historical_call."type",
                            'function_name', historical_call."function_name",
                            'arguments', historical_call."arguments"
                        ) ORDER BY historical_call."call_order"
                    )
                    FROM "conversation_llm_request_message_tool_call" historical_call
                    WHERE historical_call."request_message_id" = message."id"
                ), '[]'::JSONB)
            ) ORDER BY message."message_order"
        )
        FROM "conversation_llm_request_message" message
        WHERE message."llm_call_id" = call."id"
    ), '[]'::JSONB),
    "raw_request" = call."raw_request",
    "response_message_present" = call."response_message_present",
    "response_content" = call."response_content",
    "response_content_parts" = call."response_content_parts",
    "finish_reason" = call."finish_reason",
    "usage_present" = call."usage_present",
    "prompt_tokens" = call."prompt_tokens",
    "completion_tokens" = call."completion_tokens",
    "total_tokens" = call."total_tokens",
    "cached_prompt_tokens" = call."cached_prompt_tokens",
    "reasoning_tokens" = call."reasoning_tokens",
    "raw_response" = call."raw_response",
    "response_error_message" = call."response_error_message",
    "reasoning_content" = call."reasoning_content"
FROM "conversation_llm_call" call
WHERE call."turn_id" = turn."id";

UPDATE "conversation_turn"
SET "request_messages_snapshot_hash" = ENCODE(
    DIGEST(CAST("request_messages_snapshot" AS TEXT), 'sha256'), 'hex')
WHERE "request_messages_snapshot" IS NOT NULL;

ALTER TABLE "conversation_llm_request_message"
    ADD COLUMN IF NOT EXISTS "round_id" BIGINT,
    ADD COLUMN IF NOT EXISTS "turn_id" BIGINT;

ALTER TABLE "conversation_llm_request_message_tool_call"
    ADD COLUMN IF NOT EXISTS "round_id" BIGINT,
    ADD COLUMN IF NOT EXISTS "turn_id" BIGINT;

ALTER TABLE "conversation_llm_tool_definition"
    ADD COLUMN IF NOT EXISTS "round_id" BIGINT,
    ADD COLUMN IF NOT EXISTS "turn_id" BIGINT;

ALTER TABLE "conversation_tool_call_execution"
    ADD COLUMN IF NOT EXISTS "round_id" BIGINT,
    ADD COLUMN IF NOT EXISTS "call_order" INTEGER,
    ADD COLUMN IF NOT EXISTS "tool_call_id" VARCHAR(200),
    ADD COLUMN IF NOT EXISTS "type" VARCHAR(50),
    ADD COLUMN IF NOT EXISTS "tool_name" VARCHAR(200),
    ADD COLUMN IF NOT EXISTS "arguments" TEXT;

UPDATE "conversation_llm_request_message" message
SET "round_id" = turn."round_id",
    "turn_id" = turn."id"
FROM "conversation_llm_call" call
INNER JOIN "conversation_turn" turn ON turn."id" = call."turn_id"
WHERE message."llm_call_id" = call."id";

UPDATE "conversation_llm_request_message_tool_call" historical_call
SET "round_id" = message."round_id",
    "turn_id" = message."turn_id"
FROM "conversation_llm_request_message" message
WHERE historical_call."request_message_id" = message."id";

UPDATE "conversation_llm_tool_definition" definition
SET "round_id" = turn."round_id",
    "turn_id" = turn."id"
FROM "conversation_llm_call" call
INNER JOIN "conversation_turn" turn ON turn."id" = call."turn_id"
WHERE definition."llm_call_id" = call."id";

UPDATE "conversation_tool_call_execution" execution
SET "round_id" = turn."round_id",
    "call_order" = response_call."call_order",
    "tool_call_id" = response_call."tool_call_id",
    "type" = response_call."type",
    "tool_name" = response_call."function_name",
    "arguments" = response_call."arguments"
FROM "conversation_llm_response_tool_call" response_call
INNER JOIN "conversation_turn" turn ON turn."id" = response_call."turn_id"
WHERE execution."response_tool_call_id" = response_call."id";

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM "conversation_turn"
        WHERE "llm_start_time" IS NULL
           OR "llm_end_time" IS NULL
           OR "message_storage_mode" IS NULL
           OR "request_messages_snapshot" IS NULL
           OR "request_messages_snapshot_hash" IS NULL
    ) THEN
        RAISE EXCEPTION 'Round/Turn consolidation stopped: target Turn fields could not be backfilled.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM "conversation_llm_request_message"
        WHERE "round_id" IS NULL OR "turn_id" IS NULL
    ) THEN
        RAISE EXCEPTION 'Round/Turn consolidation stopped: a request message has no Round/Turn link.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM "conversation_llm_tool_definition"
        WHERE "round_id" IS NULL OR "turn_id" IS NULL
    ) THEN
        RAISE EXCEPTION 'Round/Turn consolidation stopped: a Tool definition has no Round/Turn link.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM "conversation_tool_call_execution"
        WHERE "round_id" IS NULL OR "call_order" IS NULL OR "tool_call_id" IS NULL
           OR "type" IS NULL OR "tool_name" IS NULL OR "arguments" IS NULL
    ) THEN
        RAISE EXCEPTION 'Round/Turn consolidation stopped: a Tool execution could not be backfilled.';
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS "idx_request_message_round_turn"
    ON "conversation_llm_request_message" ("round_id", "turn_id", "message_order");

CREATE INDEX IF NOT EXISTS "idx_request_message_tool_call_round_turn"
    ON "conversation_llm_request_message_tool_call" ("round_id", "turn_id", "call_order");

CREATE INDEX IF NOT EXISTS "idx_tool_definition_round_turn"
    ON "conversation_llm_tool_definition" ("round_id", "turn_id", "tool_order");

CREATE UNIQUE INDEX IF NOT EXISTS "uk_tool_execution_round_turn_call_order"
    ON "conversation_tool_call_execution" ("round_id", "turn_id", "call_order");

CREATE UNIQUE INDEX IF NOT EXISTS "uk_tool_execution_round_turn_call_id"
    ON "conversation_tool_call_execution" ("round_id", "turn_id", "tool_call_id");
