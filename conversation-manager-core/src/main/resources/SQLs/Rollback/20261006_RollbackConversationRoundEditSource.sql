SET TIME ZONE 'UTC';

DROP TRIGGER IF EXISTS "trg_conversation_round_edit_source_refresh_modification_time"
    ON "conversation_round_edit_source";
DROP TABLE IF EXISTS "conversation_round_edit_source";
