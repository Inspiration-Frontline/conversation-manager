DO $$
BEGIN
    IF TO_REGPROCEDURE(
        'fork_conversation_history_without_files(character varying,character varying,bigint,bigint)') IS NOT NULL THEN
        DROP FUNCTION IF EXISTS fork_conversation_history(VARCHAR, VARCHAR, BIGINT, BIGINT);

        ALTER FUNCTION fork_conversation_history_without_files(VARCHAR, VARCHAR, BIGINT, BIGINT)
            RENAME TO fork_conversation_history;
    ELSIF TO_REGPROCEDURE(
        'fork_conversation_history(character varying,character varying,bigint,bigint)') IS NULL THEN
        RAISE EXCEPTION 'fork_conversation_history is missing';
    END IF;
END;
$$;
