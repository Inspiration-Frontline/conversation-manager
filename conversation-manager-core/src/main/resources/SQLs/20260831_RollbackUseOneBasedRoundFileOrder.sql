ALTER TABLE "conversation_round_file"
    DROP CONSTRAINT "ck_conversation_round_file_order";

UPDATE "conversation_round_file"
SET "file_order" = -"file_order";

UPDATE "conversation_round_file"
SET "file_order" = -"file_order" - 1;

ALTER TABLE "conversation_round_file"
    ADD CONSTRAINT "ck_conversation_round_file_order" CHECK ("file_order" >= 0);
