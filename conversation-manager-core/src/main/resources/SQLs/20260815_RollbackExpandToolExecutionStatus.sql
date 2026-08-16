UPDATE "conversation_tool_call_execution"
SET "status" = 'FAILED',
    "error_message" = CASE
        WHEN NULLIF(BTRIM("error_message"), '') IS NULL
            THEN 'Extended Tool execution status was rolled back.'
        ELSE "error_message"
    END
WHERE "status" IN ('UNKNOWN', 'REJECTED');

ALTER TABLE "conversation_tool_call_execution"
    DROP CONSTRAINT "ck_tool_execution_status";

ALTER TABLE "conversation_tool_call_execution"
    ADD CONSTRAINT "ck_tool_execution_status" CHECK (
        ("status" = 'COMPLETED' AND "error_message" = '')
        OR ("status" = 'FAILED' AND NULLIF(BTRIM("error_message"), '') IS NOT NULL)
        OR "status" = 'CANCELLED');
