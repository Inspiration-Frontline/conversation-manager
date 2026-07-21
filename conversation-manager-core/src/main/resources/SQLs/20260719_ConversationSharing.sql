SET TIME ZONE 'UTC';

ALTER TABLE "conversation_sharing"
    ADD COLUMN IF NOT EXISTS "end_round_number" BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "expires_at" TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS "revoked" BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS "revoked_at" TIMESTAMPTZ,
    DROP COLUMN IF EXISTS "end_message_id";

CREATE INDEX IF NOT EXISTS "idx_conversation_sharing_active"
    ON "conversation_sharing" ("shared_conversation_id", "revoked", "expires_at");

UPDATE "conversation_sharing" sharing
SET "end_round_number" = COALESCE((
    SELECT MAX(round."round_number")
    FROM "conversation_round" round
    WHERE round."conversation_id" = sharing."parent_conversation_id"
      AND round."creation_time" <= sharing."creation_time"
      AND round."status" = 'COMPLETED'
      AND round."deleted" = FALSE
), 0)
WHERE sharing."end_round_number" = 0;
