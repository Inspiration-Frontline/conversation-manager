DROP FUNCTION fork_conversation_history(VARCHAR, VARCHAR, BIGINT, BIGINT);

ALTER FUNCTION fork_conversation_history_without_files(VARCHAR, VARCHAR, BIGINT, BIGINT)
    RENAME TO fork_conversation_history;
