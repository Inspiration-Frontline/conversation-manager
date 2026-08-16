ALTER TABLE "conversation_tool_call_execution"
    DROP CONSTRAINT "ck_tool_execution_status";

ALTER TABLE "conversation_tool_call_execution"
    ADD CONSTRAINT "ck_tool_execution_status" CHECK (
        ("status" = 'COMPLETED' AND "error_message" = '')
        OR ("status" IN ('FAILED', 'UNKNOWN', 'REJECTED')
            AND NULLIF(BTRIM("error_message"), '') IS NOT NULL)
        OR "status" = 'CANCELLED');
