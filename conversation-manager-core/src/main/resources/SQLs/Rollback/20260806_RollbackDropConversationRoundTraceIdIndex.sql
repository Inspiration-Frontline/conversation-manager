BEGIN;

CREATE INDEX IF NOT EXISTS "idx_conversation_round_trace_id"
    ON "conversation_round" ("trace_id")
    WHERE "trace_id" IS NOT NULL;

COMMIT;
