DROP TRIGGER IF EXISTS "trg_message_file_refresh_modification_time" ON "message_file";
DROP TRIGGER IF EXISTS "trg_conversation_sharing_refresh_modification_time" ON "conversation_sharing";
DROP TRIGGER IF EXISTS "trg_conversation_group_relation_refresh_modification_time" ON "conversation_group_relation";
DROP TRIGGER IF EXISTS "trg_conversation_group_refresh_modification_time" ON "conversation_group";
DROP TRIGGER IF EXISTS "trg_conversation_message_refresh_modification_time" ON "conversation_message";
DROP TRIGGER IF EXISTS "trg_conversation_refresh_modification_time" ON "conversation";

DROP TABLE IF EXISTS "message_file";
DROP TABLE IF EXISTS "conversation_sharing";
DROP TABLE IF EXISTS "conversation_group_relation";
DROP TABLE IF EXISTS "conversation_group";
DROP TABLE IF EXISTS "conversation_message";
DROP TABLE IF EXISTS "conversation";

DROP FUNCTION IF EXISTS "refresh_modification_time"();
