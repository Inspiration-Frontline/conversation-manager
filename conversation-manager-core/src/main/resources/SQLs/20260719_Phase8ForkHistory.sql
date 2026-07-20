CREATE OR REPLACE FUNCTION fork_conversation_history(
    p_source_conversation_id VARCHAR,
    p_target_conversation_id VARCHAR,
    p_user_id BIGINT,
    p_end_round_number BIGINT
) RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    source_round RECORD;
    source_turn RECORD;
    source_call RECORD;
    source_message RECORD;
    source_request_tool_call RECORD;
    source_tool_definition RECORD;
    source_response_tool_call RECORD;
    source_execution RECORD;
    source_file RECORD;
    target_round_id BIGINT;
    target_turn_id BIGINT;
    target_call_id BIGINT;
    target_message_id BIGINT;
    target_response_tool_call_id BIGINT;
    copied_rounds INTEGER := 0;
BEGIN
    FOR source_round IN
        SELECT *
        FROM "conversation_round"
        WHERE "conversation_id" = p_source_conversation_id
          AND "round_number" <= p_end_round_number
          AND "status" = 'COMPLETED'
          AND "deleted" = FALSE
        ORDER BY "round_number"
    LOOP
        INSERT INTO "conversation_round" (
            "creator_id", "modifier_id", "conversation_id", "round_number", "user_request_content",
            "user_request_content_parts", "final_answer_content", "final_answer_content_parts",
            "final_source_turn_number", "status", "error_message", "start_time", "end_time",
            "payload_hash_version", "payload_hash", "deleted"
        ) VALUES (
            p_user_id, p_user_id, p_target_conversation_id, source_round."round_number",
            source_round."user_request_content", source_round."user_request_content_parts",
            source_round."final_answer_content", source_round."final_answer_content_parts",
            source_round."final_source_turn_number", source_round."status", source_round."error_message",
            source_round."start_time", source_round."end_time", source_round."payload_hash_version",
            source_round."payload_hash", FALSE
        ) RETURNING "id" INTO target_round_id;

        FOR source_file IN
            SELECT "file_resource_id", "file_order"
            FROM "conversation_round_file"
            WHERE "round_id" = source_round."id"
            ORDER BY "file_order"
        LOOP
            INSERT INTO "conversation_round_file" (
                "creator_id", "modifier_id", "round_id", "file_resource_id", "file_order"
            ) VALUES (
                p_user_id, p_user_id, target_round_id, source_file."file_resource_id", source_file."file_order"
            ) ON CONFLICT DO NOTHING;
        END LOOP;

        FOR source_turn IN
            SELECT * FROM "conversation_turn"
            WHERE "round_id" = source_round."id" ORDER BY "turn_number"
        LOOP
            INSERT INTO "conversation_turn" (
                "creator_id", "modifier_id", "round_id", "turn_number", "agent_id", "agent_name",
                "agent_version", "status", "error_message", "start_time", "end_time"
            ) VALUES (
                p_user_id, p_user_id, target_round_id, source_turn."turn_number", source_turn."agent_id",
                source_turn."agent_name", source_turn."agent_version", source_turn."status",
                source_turn."error_message", source_turn."start_time", source_turn."end_time"
            ) RETURNING "id" INTO target_turn_id;

            SELECT * INTO source_call FROM "conversation_llm_call" WHERE "turn_id" = source_turn."id";
            IF FOUND THEN
                INSERT INTO "conversation_llm_call" (
                    "creator_id", "modifier_id", "turn_id", "provider", "model", "request_id", "trace_id",
                    "message_storage_mode", "tool_choice_present", "tool_choice_mode", "tool_choice_name",
                    "response_format", "temperature", "max_output_tokens", "raw_request", "start_time", "end_time",
                    "response_message_present", "response_content", "response_content_parts", "finish_reason",
                    "usage_present", "prompt_tokens", "completion_tokens", "total_tokens", "cached_prompt_tokens",
                    "reasoning_tokens", "raw_response", "response_error_message", "reasoning_content"
                ) VALUES (
                    p_user_id, p_user_id, target_turn_id, source_call."provider", source_call."model",
                    source_call."request_id", source_call."trace_id", source_call."message_storage_mode",
                    source_call."tool_choice_present", source_call."tool_choice_mode", source_call."tool_choice_name",
                    source_call."response_format", source_call."temperature", source_call."max_output_tokens",
                    source_call."raw_request", source_call."start_time", source_call."end_time",
                    source_call."response_message_present", source_call."response_content", source_call."response_content_parts",
                    source_call."finish_reason", source_call."usage_present", source_call."prompt_tokens",
                    source_call."completion_tokens", source_call."total_tokens", source_call."cached_prompt_tokens",
                    source_call."reasoning_tokens", source_call."raw_response", source_call."response_error_message",
                    source_call."reasoning_content"
                ) RETURNING "id" INTO target_call_id;

                FOR source_message IN
                    SELECT * FROM "conversation_llm_request_message"
                    WHERE "llm_call_id" = source_call."id" ORDER BY "message_order"
                LOOP
                    INSERT INTO "conversation_llm_request_message" (
                        "creator_id", "modifier_id", "llm_call_id", "message_order", "role", "content",
                        "content_parts", "tool_call_id"
                    ) VALUES (
                        p_user_id, p_user_id, target_call_id, source_message."message_order", source_message."role",
                        source_message."content", source_message."content_parts", source_message."tool_call_id"
                    ) RETURNING "id" INTO target_message_id;
                    FOR source_request_tool_call IN
                        SELECT * FROM "conversation_llm_request_message_tool_call"
                        WHERE "request_message_id" = source_message."id" ORDER BY "call_order"
                    LOOP
                        INSERT INTO "conversation_llm_request_message_tool_call" (
                            "creator_id", "modifier_id", "request_message_id", "call_order", "tool_call_id",
                            "type", "function_name", "arguments"
                        ) VALUES (
                            p_user_id, p_user_id, target_message_id, source_request_tool_call."call_order",
                            source_request_tool_call."tool_call_id", source_request_tool_call."type",
                            source_request_tool_call."function_name", source_request_tool_call."arguments"
                        );
                    END LOOP;
                END LOOP;

                FOR source_tool_definition IN
                    SELECT * FROM "conversation_llm_tool_definition"
                    WHERE "llm_call_id" = source_call."id" ORDER BY "tool_order"
                LOOP
                    INSERT INTO "conversation_llm_tool_definition" (
                        "creator_id", "modifier_id", "llm_call_id", "tool_order", "tool_key", "tool_name",
                        "source_type", "description", "parameters_json", "strict", "definition_hash"
                    ) VALUES (
                        p_user_id, p_user_id, target_call_id, source_tool_definition."tool_order",
                        source_tool_definition."tool_key", source_tool_definition."tool_name",
                        source_tool_definition."source_type", source_tool_definition."description",
                        source_tool_definition."parameters_json", source_tool_definition."strict",
                        source_tool_definition."definition_hash"
                    );
                END LOOP;
            END IF;

            FOR source_response_tool_call IN
                SELECT * FROM "conversation_llm_response_tool_call"
                WHERE "turn_id" = source_turn."id" ORDER BY "call_order"
            LOOP
                INSERT INTO "conversation_llm_response_tool_call" (
                    "creator_id", "modifier_id", "turn_id", "llm_call_id", "call_order", "tool_call_id",
                    "type", "function_name", "arguments"
                ) VALUES (
                    p_user_id, p_user_id, target_turn_id,
                    (SELECT "id" FROM "conversation_llm_call" WHERE "turn_id" = target_turn_id),
                    source_response_tool_call."call_order", source_response_tool_call."tool_call_id",
                    source_response_tool_call."type", source_response_tool_call."function_name",
                    source_response_tool_call."arguments"
                ) RETURNING "id" INTO target_response_tool_call_id;
                FOR source_execution IN
                    SELECT * FROM "conversation_tool_call_execution"
                    WHERE "response_tool_call_id" = source_response_tool_call."id"
                LOOP
                    INSERT INTO "conversation_tool_call_execution" (
                        "creator_id", "modifier_id", "turn_id", "response_tool_call_id", "execution_order",
                        "tool_key", "status", "result_content", "result_content_parts", "raw_result",
                        "error_message", "start_time", "end_time"
                    ) VALUES (
                        p_user_id, p_user_id, target_turn_id, target_response_tool_call_id,
                        source_execution."execution_order", source_execution."tool_key", source_execution."status",
                        source_execution."result_content", source_execution."result_content_parts",
                        source_execution."raw_result", source_execution."error_message", source_execution."start_time",
                        source_execution."end_time"
                    );
                END LOOP;
            END LOOP;
        END LOOP;
        copied_rounds := copied_rounds + 1;
    END LOOP;
    RETURN copied_rounds;
END;
$$;
