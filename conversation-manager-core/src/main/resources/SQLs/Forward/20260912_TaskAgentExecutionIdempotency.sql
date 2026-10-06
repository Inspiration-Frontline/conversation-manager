SET TIME ZONE 'UTC';

CREATE UNIQUE INDEX IF NOT EXISTS "uk_task_agent_execution_generation_attempt"
    ON "conversation_task_agent_execution" ("generation_attempt_id")
    WHERE "generation_attempt_id" IS NOT NULL;
