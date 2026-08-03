BEGIN;

ALTER TABLE "conversation_round"
    ADD COLUMN IF NOT EXISTS "trace_id" CHAR(32);

ALTER TABLE "conversation_round"
    DROP CONSTRAINT IF EXISTS "ck_conversation_round_trace_id";

ALTER TABLE "conversation_round"
    ADD CONSTRAINT "ck_conversation_round_trace_id"
        CHECK ("trace_id" IS NULL OR "trace_id" ~ '^[0-9a-f]{32}$');

CREATE INDEX IF NOT EXISTS "idx_conversation_round_trace_id"
    ON "conversation_round" ("trace_id")
    WHERE "trace_id" IS NOT NULL;

COMMIT;
