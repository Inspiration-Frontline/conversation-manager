SET TIME ZONE 'UTC';

CREATE TABLE IF NOT EXISTS "conversation_generation_attempt"
(
    "id"                 BIGSERIAL PRIMARY KEY,
    "creator_id"         BIGINT       NOT NULL,
    "modifier_id"        BIGINT       NOT NULL,
    "round_id"           BIGINT       NOT NULL,
    "attempt_id"         VARCHAR(100) NOT NULL,
    "capability_key"     VARCHAR(200) NOT NULL,
    "model"              VARCHAR(200) NOT NULL,
    "status"             VARCHAR(24)  NOT NULL,
    "provider_request_id" VARCHAR(200) NOT NULL DEFAULT '',
    "error_code"         VARCHAR(100) NOT NULL DEFAULT '',
    "error_message"      TEXT         NOT NULL DEFAULT '',
    "start_time"         TIMESTAMPTZ  NOT NULL,
    "end_time"           TIMESTAMPTZ,
    "creation_time"      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time"  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_generation_attempt_id" UNIQUE ("attempt_id"),
    CONSTRAINT "ck_generation_attempt_status" CHECK ("status" IN (
        'READY', 'DISPATCHING', 'COMPLETED', 'FAILED', 'CANCELLED', 'UNKNOWN', 'MATERIALIZED'
    )),
    CONSTRAINT "ck_generation_attempt_time" CHECK ("end_time" IS NULL OR "end_time" >= "start_time")
);

CREATE INDEX IF NOT EXISTS "idx_generation_attempt_round"
    ON "conversation_generation_attempt" ("round_id", "creation_time");

CREATE TABLE IF NOT EXISTS "conversation_round_generated_file"
(
    "id"                   BIGSERIAL PRIMARY KEY,
    "creator_id"           BIGINT       NOT NULL,
    "modifier_id"          BIGINT       NOT NULL,
    "round_id"             BIGINT       NOT NULL,
    "file_resource_id"     BIGINT       NOT NULL,
    "source_turn_number"   BIGINT       NOT NULL,
    "output_order"         INTEGER      NOT NULL,
    "generation_attempt_id" BIGINT      NOT NULL,
    "output_kind"          VARCHAR(32)  NOT NULL,
    "output_status"        VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    "creation_time"        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time"    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_round_generated_file_resource" UNIQUE ("round_id", "file_resource_id"),
    CONSTRAINT "uk_round_generated_file_order" UNIQUE ("round_id", "output_order"),
    CONSTRAINT "ck_round_generated_file_order" CHECK ("output_order" >= 1),
    CONSTRAINT "ck_round_generated_file_status" CHECK ("output_status" IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT "ck_round_generated_file_kind" CHECK ("output_kind" IN (
        'IMAGE', 'VIDEO', 'AUDIO', 'PRESENTATION', 'DOCUMENT', 'OTHER'
    )),
    CONSTRAINT "fk_generated_file_round" FOREIGN KEY ("round_id")
        REFERENCES "conversation_round" ("id") ON DELETE CASCADE,
    CONSTRAINT "fk_generated_file_resource" FOREIGN KEY ("file_resource_id")
        REFERENCES "file_resource" ("id"),
    CONSTRAINT "fk_generated_file_attempt" FOREIGN KEY ("generation_attempt_id")
        REFERENCES "conversation_generation_attempt" ("id")
);

CREATE INDEX IF NOT EXISTS "idx_round_generated_file_resource"
    ON "conversation_round_generated_file" ("file_resource_id");

CREATE TABLE IF NOT EXISTS "conversation_task_agent_execution"
(
    "id"                    BIGSERIAL PRIMARY KEY,
    "creator_id"            BIGINT       NOT NULL,
    "modifier_id"           BIGINT       NOT NULL,
    "round_id"              BIGINT       NOT NULL,
    "parent_turn_id"        BIGINT,
    "task_agent_id"         BIGINT       NOT NULL,
    "task_agent_name"       VARCHAR(200) NOT NULL,
    "task_agent_version"    INTEGER      NOT NULL,
    "capability_key"        VARCHAR(200) NOT NULL,
    "status"                VARCHAR(24)  NOT NULL,
    "request_id"            VARCHAR(200) NOT NULL DEFAULT '',
    "trace_id"              VARCHAR(200) NOT NULL DEFAULT '',
    "parent_span_id"        VARCHAR(200) NOT NULL DEFAULT '',
    "task_span_id"          VARCHAR(200) NOT NULL DEFAULT '',
    "rewritten_instruction" TEXT         NOT NULL DEFAULT '',
    "input_resource_ids"    JSONB,
    "normalized_settings"   JSONB,
    "generation_attempt_id" BIGINT,
    "provider_request_id"   VARCHAR(200) NOT NULL DEFAULT '',
    "error_code"            VARCHAR(100) NOT NULL DEFAULT '',
    "error_message"         TEXT         NOT NULL DEFAULT '',
    "start_time"            TIMESTAMPTZ  NOT NULL,
    "end_time"              TIMESTAMPTZ,
    "creation_time"         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time"     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "ck_task_agent_execution_status" CHECK ("status" IN (
        'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'UNKNOWN'
    )),
    CONSTRAINT "ck_task_agent_execution_identity" CHECK (
        "task_agent_id" > 0 AND "task_agent_version" > 0 AND NULLIF(BTRIM("task_agent_name"), '') IS NOT NULL
    ),
    CONSTRAINT "ck_task_agent_execution_time" CHECK ("end_time" IS NULL OR "end_time" >= "start_time")
);

CREATE INDEX IF NOT EXISTS "idx_task_agent_execution_round"
    ON "conversation_task_agent_execution" ("round_id", "creation_time");

CREATE TABLE IF NOT EXISTS "conversation_task_agent_turn"
(
    "id"                  BIGSERIAL PRIMARY KEY,
    "creator_id"          BIGINT      NOT NULL,
    "modifier_id"         BIGINT      NOT NULL,
    "task_execution_id"   BIGINT      NOT NULL,
    "turn_id"             BIGINT      NOT NULL,
    "turn_order"          INTEGER     NOT NULL,
    "creation_time"       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "modification_time"   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_task_agent_turn_order" UNIQUE ("task_execution_id", "turn_order"),
    CONSTRAINT "uk_task_agent_turn_id" UNIQUE ("task_execution_id", "turn_id"),
    CONSTRAINT "ck_task_agent_turn_order" CHECK ("turn_order" >= 1),
    CONSTRAINT "fk_task_agent_turn_execution" FOREIGN KEY ("task_execution_id")
        REFERENCES "conversation_task_agent_execution" ("id") ON DELETE CASCADE,
    CONSTRAINT "fk_task_agent_turn_turn" FOREIGN KEY ("turn_id")
        REFERENCES "conversation_turn" ("id") ON DELETE CASCADE
);

CREATE OR REPLACE FUNCTION "refresh_modification_time"()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW."modification_time" = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS "trg_generation_attempt_refresh_modification_time" ON "conversation_generation_attempt";
CREATE TRIGGER "trg_generation_attempt_refresh_modification_time"
    BEFORE UPDATE ON "conversation_generation_attempt"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_round_generated_file_refresh_modification_time" ON "conversation_round_generated_file";
CREATE TRIGGER "trg_round_generated_file_refresh_modification_time"
    BEFORE UPDATE ON "conversation_round_generated_file"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_task_agent_execution_refresh_modification_time" ON "conversation_task_agent_execution";
CREATE TRIGGER "trg_task_agent_execution_refresh_modification_time"
    BEFORE UPDATE ON "conversation_task_agent_execution"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_task_agent_turn_refresh_modification_time" ON "conversation_task_agent_turn";
CREATE TRIGGER "trg_task_agent_turn_refresh_modification_time"
    BEFORE UPDATE ON "conversation_task_agent_turn"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();
