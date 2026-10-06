BEGIN;

DROP INDEX IF EXISTS "idx_conversation_round_trace_id";

ALTER TABLE "conversation_round"
    DROP CONSTRAINT IF EXISTS "ck_conversation_round_trace_id";

ALTER TABLE "conversation_round"
    DROP COLUMN IF EXISTS "trace_id";

COMMIT;
