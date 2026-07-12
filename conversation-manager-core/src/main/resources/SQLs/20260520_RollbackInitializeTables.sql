SET TIME ZONE 'UTC';

ALTER TABLE IF EXISTS "conversation_round"
    DROP CONSTRAINT IF EXISTS "fk_round_final_source_turn";

DROP TABLE IF EXISTS "conversation_tool_call_execution";
DROP TABLE IF EXISTS "conversation_llm_response_tool_call";
DROP TABLE IF EXISTS "conversation_llm_tool_definition";
DROP TABLE IF EXISTS "conversation_llm_request_message_tool_call";
DROP TABLE IF EXISTS "conversation_llm_request_message";
DROP TABLE IF EXISTS "conversation_llm_call";
DROP TABLE IF EXISTS "conversation_turn";
DROP TABLE IF EXISTS "conversation_round";

DROP TABLE IF EXISTS "message_file";
DROP TABLE IF EXISTS "conversation_sharing";
DROP TABLE IF EXISTS "conversation_group_relation";
DROP TABLE IF EXISTS "conversation_group";
DROP TABLE IF EXISTS "conversation_message";
DROP TABLE IF EXISTS "conversation";

DROP FUNCTION IF EXISTS "refresh_modification_time"();
