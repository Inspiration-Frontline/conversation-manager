DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM "conversation_message") THEN
        RAISE EXCEPTION 'Round/Turn contract migration stopped: conversation_message is not empty.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM "conversation_turn"
        WHERE "llm_start_time" IS NULL OR "llm_end_time" IS NULL
           OR "message_storage_mode" IS NULL OR "request_messages_snapshot_hash" IS NULL
    ) THEN
        RAISE EXCEPTION 'Round/Turn contract migration stopped: Turn backfill is incomplete.';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM "conversation_llm_request_message"
        WHERE "round_id" IS NULL OR "turn_id" IS NULL
    ) THEN
        RAISE EXCEPTION 'Round/Turn contract migration stopped: request message ownership is incomplete.';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM "conversation_llm_request_message_tool_call"
        WHERE "round_id" IS NULL OR "turn_id" IS NULL
    ) THEN
        RAISE EXCEPTION 'Round/Turn contract migration stopped: request Tool-call ownership is incomplete.';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM "conversation_llm_tool_definition"
        WHERE "round_id" IS NULL OR "turn_id" IS NULL
    ) THEN
        RAISE EXCEPTION 'Round/Turn contract migration stopped: Tool-definition ownership is incomplete.';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM "conversation_tool_call_execution"
        WHERE "round_id" IS NULL OR "call_order" IS NULL OR "tool_call_id" IS NULL
           OR "type" IS NULL OR "tool_name" IS NULL OR "arguments" IS NULL
    ) THEN
        RAISE EXCEPTION 'Round/Turn contract migration stopped: Tool-execution identity is incomplete.';
    END IF;
END;
$$;

ALTER TABLE "conversation_turn"
    ALTER COLUMN "llm_start_time" SET NOT NULL,
    ALTER COLUMN "llm_end_time" SET NOT NULL,
    ALTER COLUMN "message_storage_mode" SET NOT NULL,
    ALTER COLUMN "request_messages_snapshot" SET NOT NULL,
    ALTER COLUMN "request_messages_snapshot_hash" SET NOT NULL;

ALTER TABLE "conversation_llm_request_message"
    ALTER COLUMN "round_id" SET NOT NULL,
    ALTER COLUMN "turn_id" SET NOT NULL,
    DROP CONSTRAINT IF EXISTS "uk_llm_request_message_order",
    DROP COLUMN IF EXISTS "llm_call_id";

ALTER TABLE "conversation_llm_tool_definition"
    ALTER COLUMN "round_id" SET NOT NULL,
    ALTER COLUMN "turn_id" SET NOT NULL,
    DROP CONSTRAINT IF EXISTS "uk_llm_tool_definition_order",
    DROP CONSTRAINT IF EXISTS "uk_llm_tool_definition_key",
    DROP CONSTRAINT IF EXISTS "uk_llm_tool_definition_name",
    DROP COLUMN IF EXISTS "llm_call_id";

ALTER TABLE "conversation_tool_call_execution"
    ALTER COLUMN "round_id" SET NOT NULL,
    ALTER COLUMN "call_order" SET NOT NULL,
    ALTER COLUMN "tool_call_id" SET NOT NULL,
    ALTER COLUMN "type" SET NOT NULL,
    ALTER COLUMN "tool_name" SET NOT NULL,
    ALTER COLUMN "arguments" SET NOT NULL,
    DROP CONSTRAINT IF EXISTS "uk_tool_execution_response_call",
    DROP CONSTRAINT IF EXISTS "uk_tool_execution_order",
    DROP COLUMN IF EXISTS "response_tool_call_id",
    DROP COLUMN IF EXISTS "execution_order";

ALTER TABLE "conversation_llm_request_message_tool_call"
    ALTER COLUMN "round_id" SET NOT NULL,
    ALTER COLUMN "turn_id" SET NOT NULL,
    ADD CONSTRAINT "ck_request_message_tool_call_round_turn_values"
        CHECK ("round_id" > 0 AND "turn_id" > 0);

DROP TABLE IF EXISTS "conversation_llm_response_tool_call";
DROP TABLE IF EXISTS "conversation_llm_call";

DROP TRIGGER IF EXISTS "trg_conversation_message_refresh_modification_time" ON "conversation_message";
DROP INDEX IF EXISTS "idx_conversation_message_history";
DROP TABLE IF EXISTS "conversation_message";

CREATE UNIQUE INDEX IF NOT EXISTS "uk_request_message_round_turn_order"
    ON "conversation_llm_request_message" ("round_id", "turn_id", "message_order");

CREATE UNIQUE INDEX IF NOT EXISTS "uk_llm_tool_definition_round_turn_order"
    ON "conversation_llm_tool_definition" ("round_id", "turn_id", "tool_order");

CREATE UNIQUE INDEX IF NOT EXISTS "uk_llm_tool_definition_round_turn_key"
    ON "conversation_llm_tool_definition" ("round_id", "turn_id", "tool_key");

CREATE UNIQUE INDEX IF NOT EXISTS "uk_llm_tool_definition_round_turn_name"
    ON "conversation_llm_tool_definition" ("round_id", "turn_id", "tool_name");

CREATE OR REPLACE FUNCTION fork_conversation_history(
    p_source_conversation_id VARCHAR,
    p_target_conversation_id VARCHAR,
    p_user_id BIGINT,
    p_end_round_number BIGINT
) RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    copied_rounds INTEGER;
BEGIN
    CREATE TEMPORARY TABLE fork_round_id_map (source_id BIGINT PRIMARY KEY, target_id BIGINT NOT NULL UNIQUE)
        ON COMMIT DROP;
    CREATE TEMPORARY TABLE fork_turn_id_map (source_id BIGINT PRIMARY KEY, target_id BIGINT NOT NULL UNIQUE)
        ON COMMIT DROP;
    CREATE TEMPORARY TABLE fork_request_message_id_map (source_id BIGINT PRIMARY KEY, target_id BIGINT NOT NULL UNIQUE)
        ON COMMIT DROP;

    INSERT INTO fork_round_id_map (source_id, target_id)
    SELECT source_round."id", nextval(pg_get_serial_sequence('conversation_round', 'id'))
    FROM "conversation_round" source_round
    WHERE source_round."conversation_id" = p_source_conversation_id
      AND source_round."round_number" <= p_end_round_number
      AND source_round."status" = 'COMPLETED'
      AND source_round."deleted" = FALSE;

    INSERT INTO "conversation_round" (
        "id", "creator_id", "modifier_id", "conversation_id", "round_number",
        "user_request_content", "user_request_content_parts", "final_answer_content",
        "final_answer_content_parts", "final_source_turn_number", "status", "error_message",
        "start_time", "end_time", "payload_hash_version", "payload_hash", "deleted", "trace_id"
    )
    SELECT round_map.target_id, p_user_id, p_user_id, p_target_conversation_id, source_round."round_number",
           source_round."user_request_content", source_round."user_request_content_parts",
           source_round."final_answer_content", source_round."final_answer_content_parts",
           source_round."final_source_turn_number", source_round."status", source_round."error_message",
           source_round."start_time", source_round."end_time", source_round."payload_hash_version",
           source_round."payload_hash", FALSE, source_round."trace_id"
    FROM "conversation_round" source_round
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_round."id";

    INSERT INTO fork_turn_id_map (source_id, target_id)
    SELECT source_turn."id", nextval(pg_get_serial_sequence('conversation_turn', 'id'))
    FROM "conversation_turn" source_turn
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_turn."round_id";

    INSERT INTO "conversation_turn" (
        "id", "creator_id", "modifier_id", "round_id", "turn_number", "agent_id", "agent_name",
        "agent_version", "status", "error_message", "start_time", "end_time", "llm_start_time",
        "llm_end_time", "request_id", "trace_id", "message_storage_mode", "request_messages_snapshot",
        "request_messages_snapshot_hash", "raw_request", "response_message_present", "response_content",
        "response_content_parts", "finish_reason", "usage_present", "prompt_tokens", "completion_tokens",
        "total_tokens", "cached_prompt_tokens", "reasoning_tokens", "raw_response", "response_error_message",
        "reasoning_content"
    )
    SELECT turn_map.target_id, p_user_id, p_user_id, round_map.target_id, source_turn."turn_number",
           source_turn."agent_id", source_turn."agent_name", source_turn."agent_version", source_turn."status",
           source_turn."error_message", source_turn."start_time", source_turn."end_time",
           source_turn."llm_start_time", source_turn."llm_end_time", source_turn."request_id", source_turn."trace_id",
           source_turn."message_storage_mode", source_turn."request_messages_snapshot",
           source_turn."request_messages_snapshot_hash", source_turn."raw_request",
           source_turn."response_message_present", source_turn."response_content", source_turn."response_content_parts",
           source_turn."finish_reason", source_turn."usage_present", source_turn."prompt_tokens",
           source_turn."completion_tokens", source_turn."total_tokens", source_turn."cached_prompt_tokens",
           source_turn."reasoning_tokens", source_turn."raw_response", source_turn."response_error_message",
           source_turn."reasoning_content"
    FROM "conversation_turn" source_turn
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_turn."id"
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_turn."round_id";

    INSERT INTO fork_request_message_id_map (source_id, target_id)
    SELECT source_message."id", nextval(pg_get_serial_sequence('conversation_llm_request_message', 'id'))
    FROM "conversation_llm_request_message" source_message
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_message."turn_id";

    INSERT INTO "conversation_llm_request_message" (
        "id", "creator_id", "modifier_id", "round_id", "turn_id", "message_order", "role", "content",
        "content_parts", "tool_call_id"
    )
    SELECT message_map.target_id, p_user_id, p_user_id, round_map.target_id, turn_map.target_id,
           source_message."message_order", source_message."role", source_message."content",
           source_message."content_parts", source_message."tool_call_id"
    FROM "conversation_llm_request_message" source_message
    INNER JOIN fork_request_message_id_map message_map ON message_map.source_id = source_message."id"
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_message."turn_id"
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_message."round_id";

    INSERT INTO "conversation_llm_request_message_tool_call" (
        "creator_id", "modifier_id", "round_id", "turn_id", "request_message_id", "call_order",
        "tool_call_id", "type", "function_name", "arguments"
    )
    SELECT p_user_id, p_user_id, round_map.target_id, turn_map.target_id, message_map.target_id,
           source_call."call_order", source_call."tool_call_id", source_call."type",
           source_call."function_name", source_call."arguments"
    FROM "conversation_llm_request_message_tool_call" source_call
    INNER JOIN fork_request_message_id_map message_map ON message_map.source_id = source_call."request_message_id"
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_call."turn_id"
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_call."round_id";

    INSERT INTO "conversation_llm_tool_definition" (
        "creator_id", "modifier_id", "round_id", "turn_id", "tool_order", "tool_key", "tool_name",
        "source_type", "description", "parameters_json", "strict", "definition_hash"
    )
    SELECT p_user_id, p_user_id, round_map.target_id, turn_map.target_id, source_definition."tool_order",
           source_definition."tool_key", source_definition."tool_name", source_definition."source_type",
           source_definition."description", source_definition."parameters_json", source_definition."strict",
           source_definition."definition_hash"
    FROM "conversation_llm_tool_definition" source_definition
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_definition."turn_id"
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_definition."round_id";

    INSERT INTO "conversation_tool_call_execution" (
        "creator_id", "modifier_id", "round_id", "turn_id", "call_order", "tool_call_id", "type",
        "tool_name", "arguments", "tool_key", "status", "result_content", "result_content_parts",
        "raw_result", "error_message", "start_time", "end_time"
    )
    SELECT p_user_id, p_user_id, round_map.target_id, turn_map.target_id, source_execution."call_order",
           source_execution."tool_call_id", source_execution."type", source_execution."tool_name",
           source_execution."arguments", source_execution."tool_key", source_execution."status",
           source_execution."result_content", source_execution."result_content_parts", source_execution."raw_result",
           source_execution."error_message", source_execution."start_time", source_execution."end_time"
    FROM "conversation_tool_call_execution" source_execution
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_execution."turn_id"
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_execution."round_id";

    SELECT COUNT(*) INTO copied_rounds FROM fork_round_id_map;
    RETURN copied_rounds;
END;
$$;
