DROP TRIGGER IF EXISTS "trg_tool_dispatch_refresh_modification_time" ON "conversation_tool_dispatch";
DROP TABLE IF EXISTS "conversation_tool_dispatch";
DROP TABLE IF EXISTS "conversation_round_mutation";

ALTER TABLE "conversation_round" DROP CONSTRAINT "ck_round_result";
ALTER TABLE "conversation_round" DROP CONSTRAINT "ck_round_time";
ALTER TABLE "conversation_round" DROP CONSTRAINT "ck_round_status";
ALTER TABLE "conversation_round" DROP CONSTRAINT "ck_round_revision";

UPDATE "conversation_round"
SET "status" = 'FAILED',
    "error_message" = CASE WHEN NULLIF(BTRIM("error_message"), '') IS NULL
        THEN 'Incremental execution was rolled back before completion.' ELSE "error_message" END,
    "end_time" = GREATEST("start_time", COALESCE("end_time", NOW())),
    "final_answer_content" = NULL,
    "final_answer_content_parts" = NULL,
    "final_source_turn_number" = NULL
WHERE "status" = 'IN_PROGRESS';

ALTER TABLE "conversation_round" ALTER COLUMN "end_time" SET NOT NULL;
ALTER TABLE "conversation_round"
    ADD CONSTRAINT "ck_round_status" CHECK ("status" IN ('COMPLETED', 'FAILED', 'CANCELLED')),
    ADD CONSTRAINT "ck_round_time" CHECK ("end_time" >= "start_time"),
    ADD CONSTRAINT "ck_round_result" CHECK (
        ("status" = 'COMPLETED'
            AND "error_message" = ''
            AND "final_source_turn_number" > 0
            AND ((NULLIF(BTRIM("final_answer_content"), '') IS NOT NULL
                    AND "final_answer_content_parts" IS NULL)
                OR ("final_answer_content" IS NULL
                    AND "final_answer_content_parts" IS NOT NULL
                    AND JSONB_TYPEOF("final_answer_content_parts") = 'array'
                    AND JSONB_ARRAY_LENGTH("final_answer_content_parts") > 0)))
        OR ("status" = 'FAILED'
            AND NULLIF(BTRIM("error_message"), '') IS NOT NULL
            AND "final_answer_content" IS NULL
            AND "final_answer_content_parts" IS NULL
            AND "final_source_turn_number" IS NULL)
        OR ("status" = 'CANCELLED'
            AND "final_answer_content" IS NULL
            AND "final_answer_content_parts" IS NULL
            AND "final_source_turn_number" IS NULL));

ALTER TABLE "conversation_round"
    DROP COLUMN "mcp_server_bindings",
    DROP COLUMN "agent_version",
    DROP COLUMN "agent_name",
    DROP COLUMN "agent_id",
    DROP COLUMN "revision";
