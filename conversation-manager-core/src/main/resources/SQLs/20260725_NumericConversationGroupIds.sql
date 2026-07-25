BEGIN;

CREATE TABLE "conversation_group_id_20260725_backup" AS
SELECT "id", "group_id"
FROM "conversation_group";

ALTER TABLE "conversation"
    ADD COLUMN "conversation_group_numeric_id" BIGINT;

UPDATE "conversation" conversation
SET "conversation_group_numeric_id" = conversation_group."id"
FROM "conversation_group" conversation_group
WHERE conversation."conversation_group_id" = conversation_group."group_id";

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM "conversation"
        WHERE "conversation_group_id" IS NOT NULL
          AND "conversation_group_numeric_id" IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot migrate an unknown Conversation Group ID.';
    END IF;
END
$$;

DROP INDEX IF EXISTS "idx_conversation_group_sidebar";

ALTER TABLE "conversation"
    DROP COLUMN "conversation_group_id";

ALTER TABLE "conversation"
    RENAME COLUMN "conversation_group_numeric_id" TO "conversation_group_id";

ALTER TABLE "conversation_group"
    DROP CONSTRAINT IF EXISTS "uk_conversation_group_group_id";

ALTER TABLE "conversation_group"
    DROP COLUMN "group_id";

CREATE INDEX IF NOT EXISTS "idx_conversation_root_sidebar"
    ON "conversation" ("creator_id", "pinned" DESC, "last_round_updated_time" DESC, "id" DESC)
    WHERE "deleted" = FALSE AND "conversation_group_id" IS NULL;

CREATE INDEX "idx_conversation_group_sidebar"
    ON "conversation" ("creator_id", "conversation_group_id", "last_round_updated_time" DESC, "id" DESC)
    WHERE "deleted" = FALSE AND "conversation_group_id" IS NOT NULL;

COMMIT;
