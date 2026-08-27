DROP INDEX IF EXISTS "uk_tool_execution_round_turn_call_id";
DROP INDEX IF EXISTS "uk_tool_execution_round_turn_call_order";
DROP INDEX IF EXISTS "idx_tool_definition_round_turn";
DROP INDEX IF EXISTS "idx_request_message_tool_call_round_turn";
DROP INDEX IF EXISTS "idx_request_message_round_turn";

ALTER TABLE "conversation_tool_call_execution"
    DROP COLUMN IF EXISTS "arguments",
    DROP COLUMN IF EXISTS "tool_name",
    DROP COLUMN IF EXISTS "type",
    DROP COLUMN IF EXISTS "tool_call_id",
    DROP COLUMN IF EXISTS "call_order",
    DROP COLUMN IF EXISTS "round_id";

ALTER TABLE "conversation_llm_tool_definition"
    DROP COLUMN IF EXISTS "turn_id",
    DROP COLUMN IF EXISTS "round_id";

ALTER TABLE "conversation_llm_request_message_tool_call"
    DROP COLUMN IF EXISTS "turn_id",
    DROP COLUMN IF EXISTS "round_id";

ALTER TABLE "conversation_llm_request_message"
    DROP COLUMN IF EXISTS "turn_id",
    DROP COLUMN IF EXISTS "round_id";

ALTER TABLE "conversation_turn"
    DROP COLUMN IF EXISTS "reasoning_content",
    DROP COLUMN IF EXISTS "response_error_message",
    DROP COLUMN IF EXISTS "raw_response",
    DROP COLUMN IF EXISTS "reasoning_tokens",
    DROP COLUMN IF EXISTS "cached_prompt_tokens",
    DROP COLUMN IF EXISTS "total_tokens",
    DROP COLUMN IF EXISTS "completion_tokens",
    DROP COLUMN IF EXISTS "prompt_tokens",
    DROP COLUMN IF EXISTS "usage_present",
    DROP COLUMN IF EXISTS "finish_reason",
    DROP COLUMN IF EXISTS "response_content_parts",
    DROP COLUMN IF EXISTS "response_content",
    DROP COLUMN IF EXISTS "response_message_present",
    DROP COLUMN IF EXISTS "raw_request",
    DROP COLUMN IF EXISTS "request_messages_snapshot_hash",
    DROP COLUMN IF EXISTS "request_messages_snapshot",
    DROP COLUMN IF EXISTS "message_storage_mode",
    DROP COLUMN IF EXISTS "trace_id",
    DROP COLUMN IF EXISTS "request_id",
    DROP COLUMN IF EXISTS "llm_end_time",
    DROP COLUMN IF EXISTS "llm_start_time";
