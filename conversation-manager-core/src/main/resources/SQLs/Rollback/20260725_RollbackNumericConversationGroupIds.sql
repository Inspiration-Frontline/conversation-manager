BEGIN;

ALTER TABLE "conversation_group"
    ADD COLUMN "group_id" VARCHAR(64);

UPDATE "conversation_group" conversation_group
SET "group_id" = backup."group_id"
FROM "conversation_group_id_20260725_backup" backup
WHERE conversation_group."id" = backup."id";

ALTER TABLE "conversation_group"
    ALTER COLUMN "group_id" SET NOT NULL,
    ADD CONSTRAINT "uk_conversation_group_group_id" UNIQUE ("group_id");

ALTER TABLE "conversation"
    ADD COLUMN "conversation_group_string_id" VARCHAR(64);

UPDATE "conversation" conversation
SET "conversation_group_string_id" = conversation_group."group_id"
FROM "conversation_group"
WHERE conversation."conversation_group_id" = conversation_group."id";

DROP INDEX IF EXISTS "idx_conversation_group_sidebar";

ALTER TABLE "conversation"
    DROP COLUMN "conversation_group_id";

ALTER TABLE "conversation"
    RENAME COLUMN "conversation_group_string_id" TO "conversation_group_id";

CREATE INDEX "idx_conversation_group_sidebar"
    ON "conversation" ("creator_id", "conversation_group_id", "last_round_updated_time" DESC, "id" DESC)
    WHERE "deleted" = FALSE AND "conversation_group_id" IS NOT NULL;

DROP TABLE "conversation_group_id_20260725_backup";

COMMIT;
