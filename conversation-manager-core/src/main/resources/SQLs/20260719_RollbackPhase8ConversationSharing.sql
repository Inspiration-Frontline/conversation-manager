DROP INDEX IF EXISTS "idx_conversation_sharing_active";
ALTER TABLE "conversation_sharing"
    DROP COLUMN IF EXISTS "revoked_at",
    DROP COLUMN IF EXISTS "revoked",
    DROP COLUMN IF EXISTS "expires_at",
    DROP COLUMN IF EXISTS "end_round_number";
