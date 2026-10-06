DO $$
BEGIN
    IF TO_REGPROCEDURE(
        'fork_conversation_history_without_files(character varying,character varying,bigint,bigint)') IS NULL THEN
        IF TO_REGPROCEDURE(
            'fork_conversation_history(character varying,character varying,bigint,bigint)') IS NULL THEN
            RAISE EXCEPTION 'fork_conversation_history is missing';
        END IF;

        ALTER FUNCTION fork_conversation_history(VARCHAR, VARCHAR, BIGINT, BIGINT)
            RENAME TO fork_conversation_history_without_files;
    END IF;
END;
$$;

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
    copied_rounds := fork_conversation_history_without_files(
        p_source_conversation_id,
        p_target_conversation_id,
        p_user_id,
        p_end_round_number
    );

    INSERT INTO "conversation_round_file" (
        "creator_id", "modifier_id", "round_id", "file_resource_id", "file_order"
    )
    SELECT p_user_id, p_user_id, target_round."id",
           source_file."file_resource_id", source_file."file_order"
    FROM "conversation_round" source_round
    INNER JOIN "conversation_round" target_round
        ON target_round."conversation_id" = p_target_conversation_id
       AND target_round."round_number" = source_round."round_number"
    INNER JOIN "conversation_round_file" source_file
        ON source_file."round_id" = source_round."id"
    WHERE source_round."conversation_id" = p_source_conversation_id
      AND source_round."round_number" <= p_end_round_number
      AND source_round."status" = 'COMPLETED'
      AND source_round."deleted" = FALSE
    ON CONFLICT DO NOTHING;

    RETURN copied_rounds;
END;
$$;
