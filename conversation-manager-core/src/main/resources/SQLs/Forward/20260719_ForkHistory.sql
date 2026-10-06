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
    CREATE TEMPORARY TABLE fork_round_id_map
    (
        source_id BIGINT PRIMARY KEY,
        target_id BIGINT NOT NULL UNIQUE
    ) ON COMMIT DROP;

    CREATE TEMPORARY TABLE fork_turn_id_map
    (
        source_id BIGINT PRIMARY KEY,
        target_id BIGINT NOT NULL UNIQUE
    ) ON COMMIT DROP;

    CREATE TEMPORARY TABLE fork_llm_call_id_map
    (
        source_id BIGINT PRIMARY KEY,
        target_id BIGINT NOT NULL UNIQUE
    ) ON COMMIT DROP;

    CREATE TEMPORARY TABLE fork_request_message_id_map
    (
        source_id BIGINT PRIMARY KEY,
        target_id BIGINT NOT NULL UNIQUE
    ) ON COMMIT DROP;

    CREATE TEMPORARY TABLE fork_response_tool_call_id_map
    (
        source_id BIGINT PRIMARY KEY,
        target_id BIGINT NOT NULL UNIQUE
    ) ON COMMIT DROP;

    INSERT INTO fork_round_id_map (source_id, target_id)
    SELECT source_round."id",
           nextval(pg_get_serial_sequence('conversation_round', 'id'))
    FROM "conversation_round" source_round
    WHERE source_round."conversation_id" = p_source_conversation_id
      AND source_round."round_number" <= p_end_round_number
      AND source_round."status" = 'COMPLETED'
      AND source_round."deleted" = FALSE;

    INSERT INTO "conversation_round" (
        "id", "creator_id", "modifier_id", "conversation_id", "round_number",
        "user_request_content", "user_request_content_parts", "final_answer_content",
        "final_answer_content_parts", "final_source_turn_number", "status", "error_message",
        "start_time", "end_time", "payload_hash_version", "payload_hash", "deleted"
    )
    SELECT round_map.target_id, p_user_id, p_user_id, p_target_conversation_id,
           source_round."round_number", source_round."user_request_content",
           source_round."user_request_content_parts", source_round."final_answer_content",
           source_round."final_answer_content_parts", source_round."final_source_turn_number",
           source_round."status", source_round."error_message", source_round."start_time",
           source_round."end_time", source_round."payload_hash_version", source_round."payload_hash", FALSE
    FROM "conversation_round" source_round
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_round."id";

    INSERT INTO "conversation_round_file" (
        "creator_id", "modifier_id", "round_id", "file_resource_id", "file_order"
    )
    SELECT p_user_id, p_user_id, round_map.target_id,
           source_file."file_resource_id", source_file."file_order"
    FROM "conversation_round_file" source_file
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_file."round_id"
    ON CONFLICT DO NOTHING;

    INSERT INTO fork_turn_id_map (source_id, target_id)
    SELECT source_turn."id",
           nextval(pg_get_serial_sequence('conversation_turn', 'id'))
    FROM "conversation_turn" source_turn
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_turn."round_id";

    INSERT INTO "conversation_turn" (
        "id", "creator_id", "modifier_id", "round_id", "turn_number", "agent_id",
        "agent_name", "agent_version", "status", "error_message", "start_time", "end_time"
    )
    SELECT turn_map.target_id, p_user_id, p_user_id, round_map.target_id,
           source_turn."turn_number", source_turn."agent_id", source_turn."agent_name",
           source_turn."agent_version", source_turn."status", source_turn."error_message",
           source_turn."start_time", source_turn."end_time"
    FROM "conversation_turn" source_turn
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_turn."id"
    INNER JOIN fork_round_id_map round_map ON round_map.source_id = source_turn."round_id";

    INSERT INTO fork_llm_call_id_map (source_id, target_id)
    SELECT source_call."id",
           nextval(pg_get_serial_sequence('conversation_llm_call', 'id'))
    FROM "conversation_llm_call" source_call
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_call."turn_id";

    INSERT INTO "conversation_llm_call" (
        "id", "creator_id", "modifier_id", "turn_id", "provider", "model", "request_id",
        "trace_id", "message_storage_mode", "tool_choice_present", "tool_choice_mode",
        "tool_choice_name", "response_format", "temperature", "max_output_tokens", "raw_request",
        "start_time", "end_time", "response_message_present", "response_content",
        "response_content_parts", "finish_reason", "usage_present", "prompt_tokens",
        "completion_tokens", "total_tokens", "cached_prompt_tokens", "reasoning_tokens",
        "raw_response", "response_error_message", "reasoning_content"
    )
    SELECT call_map.target_id, p_user_id, p_user_id, turn_map.target_id,
           source_call."provider", source_call."model", source_call."request_id", source_call."trace_id",
           source_call."message_storage_mode", source_call."tool_choice_present",
           source_call."tool_choice_mode", source_call."tool_choice_name", source_call."response_format",
           source_call."temperature", source_call."max_output_tokens", source_call."raw_request",
           source_call."start_time", source_call."end_time", source_call."response_message_present",
           source_call."response_content", source_call."response_content_parts", source_call."finish_reason",
           source_call."usage_present", source_call."prompt_tokens", source_call."completion_tokens",
           source_call."total_tokens", source_call."cached_prompt_tokens", source_call."reasoning_tokens",
           source_call."raw_response", source_call."response_error_message", source_call."reasoning_content"
    FROM "conversation_llm_call" source_call
    INNER JOIN fork_llm_call_id_map call_map ON call_map.source_id = source_call."id"
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_call."turn_id";

    INSERT INTO fork_request_message_id_map (source_id, target_id)
    SELECT source_message."id",
           nextval(pg_get_serial_sequence('conversation_llm_request_message', 'id'))
    FROM "conversation_llm_request_message" source_message
    INNER JOIN fork_llm_call_id_map call_map ON call_map.source_id = source_message."llm_call_id";

    INSERT INTO "conversation_llm_request_message" (
        "id", "creator_id", "modifier_id", "llm_call_id", "message_order", "role",
        "content", "content_parts", "tool_call_id"
    )
    SELECT message_map.target_id, p_user_id, p_user_id, call_map.target_id,
           source_message."message_order", source_message."role", source_message."content",
           source_message."content_parts", source_message."tool_call_id"
    FROM "conversation_llm_request_message" source_message
    INNER JOIN fork_request_message_id_map message_map ON message_map.source_id = source_message."id"
    INNER JOIN fork_llm_call_id_map call_map ON call_map.source_id = source_message."llm_call_id";

    INSERT INTO "conversation_llm_request_message_tool_call" (
        "creator_id", "modifier_id", "request_message_id", "call_order", "tool_call_id",
        "type", "function_name", "arguments"
    )
    SELECT p_user_id, p_user_id, message_map.target_id, source_tool_call."call_order",
           source_tool_call."tool_call_id", source_tool_call."type",
           source_tool_call."function_name", source_tool_call."arguments"
    FROM "conversation_llm_request_message_tool_call" source_tool_call
    INNER JOIN fork_request_message_id_map message_map
        ON message_map.source_id = source_tool_call."request_message_id";

    INSERT INTO "conversation_llm_tool_definition" (
        "creator_id", "modifier_id", "llm_call_id", "tool_order", "tool_key", "tool_name",
        "source_type", "description", "parameters_json", "strict", "definition_hash"
    )
    SELECT p_user_id, p_user_id, call_map.target_id, source_definition."tool_order",
           source_definition."tool_key", source_definition."tool_name", source_definition."source_type",
           source_definition."description", source_definition."parameters_json", source_definition."strict",
           source_definition."definition_hash"
    FROM "conversation_llm_tool_definition" source_definition
    INNER JOIN fork_llm_call_id_map call_map ON call_map.source_id = source_definition."llm_call_id";

    INSERT INTO fork_response_tool_call_id_map (source_id, target_id)
    SELECT source_tool_call."id",
           nextval(pg_get_serial_sequence('conversation_llm_response_tool_call', 'id'))
    FROM "conversation_llm_response_tool_call" source_tool_call
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_tool_call."turn_id";

    INSERT INTO "conversation_llm_response_tool_call" (
        "id", "creator_id", "modifier_id", "turn_id", "llm_call_id", "call_order",
        "tool_call_id", "type", "function_name", "arguments"
    )
    SELECT response_map.target_id, p_user_id, p_user_id, turn_map.target_id, call_map.target_id,
           source_tool_call."call_order", source_tool_call."tool_call_id", source_tool_call."type",
           source_tool_call."function_name", source_tool_call."arguments"
    FROM "conversation_llm_response_tool_call" source_tool_call
    INNER JOIN fork_response_tool_call_id_map response_map ON response_map.source_id = source_tool_call."id"
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_tool_call."turn_id"
    INNER JOIN fork_llm_call_id_map call_map ON call_map.source_id = source_tool_call."llm_call_id";

    INSERT INTO "conversation_tool_call_execution" (
        "creator_id", "modifier_id", "turn_id", "response_tool_call_id", "execution_order",
        "tool_key", "status", "result_content", "result_content_parts", "raw_result",
        "error_message", "start_time", "end_time"
    )
    SELECT p_user_id, p_user_id, turn_map.target_id, response_map.target_id,
           source_execution."execution_order", source_execution."tool_key", source_execution."status",
           source_execution."result_content", source_execution."result_content_parts",
           source_execution."raw_result", source_execution."error_message",
           source_execution."start_time", source_execution."end_time"
    FROM "conversation_tool_call_execution" source_execution
    INNER JOIN fork_turn_id_map turn_map ON turn_map.source_id = source_execution."turn_id"
    INNER JOIN fork_response_tool_call_id_map response_map
        ON response_map.source_id = source_execution."response_tool_call_id";

    SELECT COUNT(*) INTO copied_rounds FROM fork_round_id_map;
    RETURN copied_rounds;
END;
$$;
