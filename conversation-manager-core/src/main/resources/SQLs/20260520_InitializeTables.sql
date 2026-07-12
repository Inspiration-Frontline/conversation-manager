SET TIME ZONE 'UTC';

CREATE TABLE IF NOT EXISTS "conversation"
(
    "id"                  BIGSERIAL PRIMARY KEY,
    "creator_id"          BIGINT       NOT NULL,
    "creation_time"       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modifier_id"         BIGINT       NOT NULL,
    "modification_time"   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "conversation_id"     VARCHAR(64)  NOT NULL,
    "title"               VARCHAR(200) NOT NULL,
    "pinned"              BOOLEAN      NOT NULL DEFAULT FALSE,
    "latest_round_number" BIGINT       NOT NULL DEFAULT 0,
    "deleted"             BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT "uk_conversation_conversation_id" UNIQUE ("conversation_id"),
    CONSTRAINT "ck_conversation_latest_round_number" CHECK ("latest_round_number" >= 0)
);

CREATE INDEX IF NOT EXISTS "idx_conversation_creator_deleted_pinned_modified"
    ON "conversation" ("creator_id", "deleted", "pinned" DESC, "modification_time" DESC);

CREATE TABLE IF NOT EXISTS "conversation_message"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT      NOT NULL,
    "creation_time"     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "modifier_id"       BIGINT      NOT NULL,
    "modification_time" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "conversation_id"   VARCHAR(64) NOT NULL,
    "role"              VARCHAR(32) NOT NULL,
    "name"              VARCHAR(128),
    "content"           TEXT,
    "content_parts"     TEXT,
    "tool_calls"        TEXT,
    "tool_call_id"      VARCHAR(128),
    "agent_id"          BIGINT,
    "finish_reason"     VARCHAR(64),
    "deleted"           BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS "idx_conversation_message_history"
    ON "conversation_message" ("conversation_id", "deleted", "id");

CREATE TABLE IF NOT EXISTS "conversation_round"
(
    "id"                         BIGSERIAL PRIMARY KEY,
    "creator_id"                 BIGINT      NOT NULL,
    "modifier_id"                BIGINT      NOT NULL,
    "conversation_id"            VARCHAR(64) NOT NULL,
    "round_number"               BIGINT      NOT NULL,
    "user_request_content"       TEXT,
    "user_request_content_parts" JSONB,
    "final_answer_content"       TEXT,
    "final_answer_content_parts" JSONB,
    "final_source_turn_number"   BIGINT,
    "status"                     VARCHAR(16) NOT NULL,
    "error_message"              TEXT        NOT NULL DEFAULT '',
    "start_time"                 TIMESTAMPTZ NOT NULL,
    "end_time"                   TIMESTAMPTZ NOT NULL,
    "payload_hash_version"       SMALLINT    NOT NULL,
    "payload_hash"               CHAR(64)    NOT NULL,
    "deleted"                    BOOLEAN     NOT NULL DEFAULT FALSE,
    "deletion_time"              TIMESTAMPTZ,
    "deleted_by"                 BIGINT,
    "creation_time"              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "modification_time"          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_round_conversation_number" UNIQUE ("conversation_id", "round_number"),
    CONSTRAINT "ck_round_number" CHECK ("round_number" > 0),
    CONSTRAINT "ck_round_status" CHECK ("status" IN ('COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT "ck_round_time" CHECK ("end_time" >= "start_time"),
    CONSTRAINT "ck_round_payload_hash" CHECK (
        "payload_hash_version" > 0
            AND "payload_hash" ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT "ck_round_user_request" CHECK (
        (
            NULLIF(BTRIM("user_request_content"), '') IS NOT NULL
                AND "user_request_content_parts" IS NULL
            )
            OR
        (
            "user_request_content" IS NULL
                AND "user_request_content_parts" IS NOT NULL
                AND CASE
                        WHEN JSONB_TYPEOF("user_request_content_parts") = 'array'
                            THEN JSONB_ARRAY_LENGTH("user_request_content_parts") > 0
                        ELSE FALSE
                END
            )
        ),
    CONSTRAINT "ck_round_result" CHECK (
        (
            "status" = 'COMPLETED'
                AND "error_message" = ''
                AND "final_source_turn_number" IS NOT NULL
                AND "final_source_turn_number" > 0
                AND (
                (
                    NULLIF(BTRIM("final_answer_content"), '') IS NOT NULL
                        AND "final_answer_content_parts" IS NULL
                    )
                    OR
                (
                    "final_answer_content" IS NULL
                        AND "final_answer_content_parts" IS NOT NULL
                        AND CASE
                                WHEN JSONB_TYPEOF("final_answer_content_parts") = 'array'
                                    THEN JSONB_ARRAY_LENGTH("final_answer_content_parts") > 0
                                ELSE FALSE
                        END
                    )
                )
            )
            OR
        (
            "status" = 'FAILED'
                AND NULLIF(BTRIM("error_message"), '') IS NOT NULL
                AND "final_source_turn_number" IS NULL
                AND "final_answer_content" IS NULL
                AND "final_answer_content_parts" IS NULL
            )
            OR
        (
            "status" = 'CANCELLED'
                AND "final_source_turn_number" IS NULL
                AND "final_answer_content" IS NULL
                AND "final_answer_content_parts" IS NULL
            )
        ),
    CONSTRAINT "ck_round_deletion" CHECK (
        (
            "deleted" = FALSE
                AND "deletion_time" IS NULL
                AND "deleted_by" IS NULL
            )
            OR
        (
            "deleted" = TRUE
                AND "deletion_time" IS NOT NULL
                AND "deleted_by" IS NOT NULL
                AND "deleted_by" > 0
            )
        )
);

CREATE INDEX IF NOT EXISTS "idx_round_active_history"
    ON "conversation_round" ("conversation_id", "round_number")
    WHERE "deleted" = FALSE;

CREATE TABLE IF NOT EXISTS "conversation_turn"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT       NOT NULL,
    "modifier_id"       BIGINT       NOT NULL,
    "round_id"          BIGINT       NOT NULL,
    "turn_number"       BIGINT       NOT NULL,
    "agent_id"          BIGINT       NOT NULL,
    "agent_name"        VARCHAR(200) NOT NULL,
    "agent_version"     INTEGER      NOT NULL,
    "status"            VARCHAR(16)  NOT NULL,
    "error_message"     TEXT         NOT NULL DEFAULT '',
    "start_time"        TIMESTAMPTZ  NOT NULL,
    "end_time"          TIMESTAMPTZ  NOT NULL,
    "creation_time"     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time" TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_turn_round_number" UNIQUE ("round_id", "turn_number"),
    CONSTRAINT "ck_turn_number" CHECK ("turn_number" > 0),
    CONSTRAINT "ck_turn_agent" CHECK (
        "agent_id" > 0
            AND NULLIF(BTRIM("agent_name"), '') IS NOT NULL
            AND "agent_version" > 0
        ),
    CONSTRAINT "ck_turn_status" CHECK (
        ("status" = 'COMPLETED' AND "error_message" = '')
            OR ("status" = 'FAILED' AND NULLIF(BTRIM("error_message"), '') IS NOT NULL)
            OR "status" = 'CANCELLED'
        ),
    CONSTRAINT "ck_turn_time" CHECK ("end_time" >= "start_time")
);

CREATE TABLE IF NOT EXISTS "conversation_llm_call"
(
    "id"                       BIGSERIAL PRIMARY KEY,
    "creator_id"               BIGINT       NOT NULL,
    "modifier_id"              BIGINT       NOT NULL,
    "turn_id"                  BIGINT       NOT NULL,
    "provider"                 VARCHAR(100) NOT NULL,
    "model"                    VARCHAR(200) NOT NULL,
    "request_id"               VARCHAR(200) NOT NULL DEFAULT '',
    "trace_id"                 VARCHAR(200) NOT NULL DEFAULT '',
    "message_storage_mode"     VARCHAR(32)  NOT NULL,
    "tool_choice_present"      BOOLEAN      NOT NULL DEFAULT FALSE,
    "tool_choice_mode"         VARCHAR(16),
    "tool_choice_name"         VARCHAR(200),
    "response_format"          TEXT,
    "temperature"              DOUBLE PRECISION,
    "max_output_tokens"        BIGINT,
    "raw_request"              TEXT,
    "start_time"               TIMESTAMPTZ  NOT NULL,
    "end_time"                 TIMESTAMPTZ  NOT NULL,
    "response_message_present" BOOLEAN      NOT NULL,
    "response_content"         TEXT,
    "response_content_parts"   JSONB,
    "finish_reason"            VARCHAR(100) NOT NULL DEFAULT '',
    "usage_present"            BOOLEAN      NOT NULL DEFAULT FALSE,
    "prompt_tokens"            BIGINT,
    "completion_tokens"        BIGINT,
    "total_tokens"             BIGINT,
    "cached_prompt_tokens"     BIGINT,
    "reasoning_tokens"         BIGINT,
    "raw_response"             TEXT,
    "response_error_message"   TEXT         NOT NULL DEFAULT '',
    "reasoning_content"        TEXT,
    "creation_time"            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time"        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_llm_call_turn" UNIQUE ("turn_id"),
    CONSTRAINT "ck_llm_call_identity" CHECK (
        NULLIF(BTRIM("provider"), '') IS NOT NULL
            AND NULLIF(BTRIM("model"), '') IS NOT NULL
        ),
    CONSTRAINT "ck_llm_call_storage_mode" CHECK ("message_storage_mode" IN ('FULL_SNAPSHOT', 'APPEND_DELTA')),
    CONSTRAINT "ck_llm_call_tool_choice" CHECK (
        (
            "tool_choice_present" = FALSE
                AND "tool_choice_mode" IS NULL
                AND "tool_choice_name" IS NULL
            )
            OR
        (
            "tool_choice_present" = TRUE
                AND "tool_choice_mode" IN ('AUTO', 'NONE', 'REQUIRED')
                AND "tool_choice_name" IS NULL
            )
            OR
        (
            "tool_choice_present" = TRUE
                AND "tool_choice_mode" = 'SPECIFIC'
                AND NULLIF(BTRIM("tool_choice_name"), '') IS NOT NULL
            )
        ),
    CONSTRAINT "ck_llm_call_max_output_tokens" CHECK ("max_output_tokens" IS NULL OR "max_output_tokens" > 0),
    CONSTRAINT "ck_llm_call_time" CHECK ("end_time" >= "start_time"),
    CONSTRAINT "ck_llm_call_response_content" CHECK (
        NOT ("response_content" IS NOT NULL AND "response_content_parts" IS NOT NULL)
            AND (
            "response_content_parts" IS NULL
                OR CASE
                       WHEN JSONB_TYPEOF("response_content_parts") = 'array'
                           THEN JSONB_ARRAY_LENGTH("response_content_parts") > 0
                       ELSE FALSE
                END
            )
        ),
    CONSTRAINT "ck_llm_call_response" CHECK (
        (
            "response_error_message" = ''
                AND "response_message_present" = TRUE
                AND NULLIF(BTRIM("finish_reason"), '') IS NOT NULL
            )
            OR
        (
            NULLIF(BTRIM("response_error_message"), '') IS NOT NULL
                AND "response_message_present" = FALSE
                AND "response_content" IS NULL
                AND "response_content_parts" IS NULL
                AND "finish_reason" = ''
            )
        ),
    CONSTRAINT "ck_llm_call_usage" CHECK (
        (
            "usage_present" = FALSE
                AND "prompt_tokens" IS NULL
                AND "completion_tokens" IS NULL
                AND "total_tokens" IS NULL
                AND "cached_prompt_tokens" IS NULL
                AND "reasoning_tokens" IS NULL
            )
            OR
        (
            "usage_present" = TRUE
                AND "prompt_tokens" IS NOT NULL
                AND "completion_tokens" IS NOT NULL
                AND "total_tokens" IS NOT NULL
                AND "cached_prompt_tokens" IS NOT NULL
                AND "reasoning_tokens" IS NOT NULL
                AND "prompt_tokens" >= 0
                AND "completion_tokens" >= 0
                AND "total_tokens" >= 0
                AND "cached_prompt_tokens" >= 0
                AND "reasoning_tokens" >= 0
            )
        )
);

CREATE TABLE IF NOT EXISTS "conversation_llm_request_message"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT      NOT NULL,
    "modifier_id"       BIGINT      NOT NULL,
    "llm_call_id"       BIGINT      NOT NULL,
    "message_order"     INTEGER     NOT NULL,
    "role"              VARCHAR(16) NOT NULL,
    "content"           TEXT,
    "content_parts"     JSONB,
    "tool_call_id"      VARCHAR(200),
    "creation_time"     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "modification_time" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_llm_request_message_order" UNIQUE ("llm_call_id", "message_order"),
    CONSTRAINT "ck_llm_request_message_order" CHECK ("message_order" >= 0),
    CONSTRAINT "ck_llm_request_message_role" CHECK ("role" IN ('SYSTEM', 'USER', 'ASSISTANT', 'TOOL', 'DEVELOPER')),
    CONSTRAINT "ck_llm_request_message_content" CHECK (
        NOT ("content" IS NOT NULL AND "content_parts" IS NOT NULL)
            AND (
            "content_parts" IS NULL
                OR CASE
                       WHEN JSONB_TYPEOF("content_parts") = 'array'
                           THEN JSONB_ARRAY_LENGTH("content_parts") > 0
                       ELSE FALSE
                END
            )
            AND (
            "role" = 'ASSISTANT'
                OR NULLIF(BTRIM("content"), '') IS NOT NULL
                OR "content_parts" IS NOT NULL
            )
        ),
    CONSTRAINT "ck_llm_request_message_tool_call_id" CHECK (
        ("role" = 'TOOL' AND NULLIF(BTRIM("tool_call_id"), '') IS NOT NULL)
            OR ("role" != 'TOOL' AND "tool_call_id" IS NULL)
        )
);

CREATE TABLE IF NOT EXISTS "conversation_llm_request_message_tool_call"
(
    "id"                 BIGSERIAL PRIMARY KEY,
    "creator_id"         BIGINT       NOT NULL,
    "modifier_id"        BIGINT       NOT NULL,
    "request_message_id" BIGINT       NOT NULL,
    "call_order"         INTEGER      NOT NULL,
    "tool_call_id"       VARCHAR(200) NOT NULL,
    "type"               VARCHAR(50)  NOT NULL,
    "function_name"      VARCHAR(200) NOT NULL,
    "arguments"          TEXT         NOT NULL,
    "creation_time"      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time"  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_request_message_tool_call_order" UNIQUE ("request_message_id", "call_order"),
    CONSTRAINT "uk_request_message_tool_call_id" UNIQUE ("request_message_id", "tool_call_id"),
    CONSTRAINT "ck_request_message_tool_call_order" CHECK ("call_order" >= 0),
    CONSTRAINT "ck_request_message_tool_call_values" CHECK (
        NULLIF(BTRIM("tool_call_id"), '') IS NOT NULL
            AND NULLIF(BTRIM("type"), '') IS NOT NULL
            AND NULLIF(BTRIM("function_name"), '') IS NOT NULL
            AND NULLIF(BTRIM("arguments"), '') IS NOT NULL
        )
);

CREATE TABLE IF NOT EXISTS "conversation_llm_tool_definition"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT       NOT NULL,
    "modifier_id"       BIGINT       NOT NULL,
    "llm_call_id"       BIGINT       NOT NULL,
    "tool_order"        INTEGER      NOT NULL,
    "tool_key"          VARCHAR(200) NOT NULL,
    "tool_name"         VARCHAR(200) NOT NULL,
    "source_type"       VARCHAR(16)  NOT NULL,
    "description"       TEXT         NOT NULL DEFAULT '',
    "parameters_json"   TEXT         NOT NULL,
    "strict"            BOOLEAN      NOT NULL DEFAULT FALSE,
    "definition_hash"   CHAR(64)     NOT NULL,
    "creation_time"     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time" TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_llm_tool_definition_order" UNIQUE ("llm_call_id", "tool_order"),
    CONSTRAINT "uk_llm_tool_definition_key" UNIQUE ("llm_call_id", "tool_key"),
    CONSTRAINT "uk_llm_tool_definition_name" UNIQUE ("llm_call_id", "tool_name"),
    CONSTRAINT "ck_llm_tool_definition_order" CHECK ("tool_order" >= 0),
    CONSTRAINT "ck_llm_tool_definition_source_type" CHECK ("source_type" IN ('INTERNAL', 'BUSINESS', 'MCP')),
    CONSTRAINT "ck_llm_tool_definition_hash" CHECK ("definition_hash" ~ '^[0-9a-f]{64}$'),
    CONSTRAINT "ck_llm_tool_definition_values" CHECK (
        NULLIF(BTRIM("tool_key"), '') IS NOT NULL
            AND NULLIF(BTRIM("tool_name"), '') IS NOT NULL
            AND NULLIF(BTRIM("parameters_json"), '') IS NOT NULL
        )
);

CREATE TABLE IF NOT EXISTS "conversation_llm_response_tool_call"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT       NOT NULL,
    "modifier_id"       BIGINT       NOT NULL,
    "turn_id"           BIGINT       NOT NULL,
    "llm_call_id"       BIGINT       NOT NULL,
    "call_order"        INTEGER      NOT NULL,
    "tool_call_id"      VARCHAR(200) NOT NULL,
    "type"              VARCHAR(50)  NOT NULL,
    "function_name"     VARCHAR(200) NOT NULL,
    "arguments"         TEXT         NOT NULL,
    "creation_time"     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time" TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_response_tool_call_order" UNIQUE ("llm_call_id", "call_order"),
    CONSTRAINT "uk_response_tool_call_external_id" UNIQUE ("llm_call_id", "tool_call_id"),
    CONSTRAINT "ck_response_tool_call_order" CHECK ("call_order" >= 0),
    CONSTRAINT "ck_response_tool_call_values" CHECK (
        NULLIF(BTRIM("tool_call_id"), '') IS NOT NULL
            AND NULLIF(BTRIM("type"), '') IS NOT NULL
            AND NULLIF(BTRIM("function_name"), '') IS NOT NULL
            AND NULLIF(BTRIM("arguments"), '') IS NOT NULL
        )
);

CREATE INDEX IF NOT EXISTS "idx_response_tool_call_turn"
    ON "conversation_llm_response_tool_call" ("turn_id");

CREATE TABLE IF NOT EXISTS "conversation_tool_call_execution"
(
    "id"                    BIGSERIAL PRIMARY KEY,
    "creator_id"            BIGINT       NOT NULL,
    "modifier_id"           BIGINT       NOT NULL,
    "turn_id"               BIGINT       NOT NULL,
    "response_tool_call_id" BIGINT       NOT NULL,
    "execution_order"       INTEGER      NOT NULL,
    "tool_key"              VARCHAR(200) NOT NULL,
    "status"                VARCHAR(16)  NOT NULL,
    "result_content"        TEXT,
    "result_content_parts"  JSONB,
    "raw_result"            TEXT,
    "error_message"         TEXT         NOT NULL DEFAULT '',
    "start_time"            TIMESTAMPTZ  NOT NULL,
    "end_time"              TIMESTAMPTZ  NOT NULL,
    "creation_time"         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time"     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_tool_execution_response_call" UNIQUE ("response_tool_call_id"),
    CONSTRAINT "uk_tool_execution_order" UNIQUE ("turn_id", "execution_order"),
    CONSTRAINT "ck_tool_execution_order" CHECK ("execution_order" >= 0),
    CONSTRAINT "ck_tool_execution_tool_key" CHECK (NULLIF(BTRIM("tool_key"), '') IS NOT NULL),
    CONSTRAINT "ck_tool_execution_status" CHECK (
        ("status" = 'COMPLETED' AND "error_message" = '')
            OR ("status" = 'FAILED' AND NULLIF(BTRIM("error_message"), '') IS NOT NULL)
            OR "status" = 'CANCELLED'
        ),
    CONSTRAINT "ck_tool_execution_result" CHECK (
        NOT ("result_content" IS NOT NULL AND "result_content_parts" IS NOT NULL)
            AND (
            "result_content_parts" IS NULL
                OR CASE
                       WHEN JSONB_TYPEOF("result_content_parts") = 'array'
                           THEN JSONB_ARRAY_LENGTH("result_content_parts") > 0
                       ELSE FALSE
                END
            )
        ),
    CONSTRAINT "ck_tool_execution_time" CHECK ("end_time" >= "start_time")
);

CREATE TABLE IF NOT EXISTS "conversation_group"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT       NOT NULL,
    "creation_time"     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modifier_id"       BIGINT       NOT NULL,
    "modification_time" TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "group_id"          VARCHAR(64)  NOT NULL,
    "name"              VARCHAR(100) NOT NULL,
    "description"       TEXT,
    "sort_order"        INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT "uk_conversation_group_group_id" UNIQUE ("group_id")
);

CREATE INDEX IF NOT EXISTS "idx_conversation_group_creator_sort"
    ON "conversation_group" ("creator_id", "sort_order", "id");

CREATE TABLE IF NOT EXISTS "conversation_group_relation"
(
    "id"                    BIGSERIAL PRIMARY KEY,
    "creator_id"            BIGINT      NOT NULL,
    "creation_time"         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "modifier_id"           BIGINT      NOT NULL,
    "modification_time"     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "conversation_id"       VARCHAR(64) NOT NULL,
    "conversation_group_id" VARCHAR(64) NOT NULL,
    "sort_order"            INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT "uk_conversation_group_relation_group"
        UNIQUE ("creator_id", "conversation_group_id", "conversation_id")
);

CREATE INDEX IF NOT EXISTS "idx_conversation_group_relation_group_sort"
    ON "conversation_group_relation" ("conversation_group_id", "sort_order" DESC, "id" DESC);

CREATE INDEX IF NOT EXISTS "idx_conversation_group_relation_conversation"
    ON "conversation_group_relation" ("creator_id", "conversation_id");

CREATE TABLE IF NOT EXISTS "conversation_sharing"
(
    "id"                       BIGSERIAL PRIMARY KEY,
    "creator_id"               BIGINT      NOT NULL,
    "creation_time"            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "modifier_id"              BIGINT      NOT NULL,
    "modification_time"        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "parent_conversation_id"   VARCHAR(64) NOT NULL,
    "shared_conversation_id"   VARCHAR(64) NOT NULL,
    "end_message_id"           BIGINT      NOT NULL DEFAULT 0,
    "accessible_after_deleted" BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT "uk_conversation_sharing_shared_id" UNIQUE ("shared_conversation_id")
);

CREATE INDEX IF NOT EXISTS "idx_conversation_sharing_parent"
    ON "conversation_sharing" ("parent_conversation_id");

CREATE TABLE IF NOT EXISTS "message_file"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT        NOT NULL,
    "creation_time"     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    "modifier_id"       BIGINT        NOT NULL,
    "modification_time" TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    "file_type"         VARCHAR(32)   NOT NULL,
    "oss_url"           VARCHAR(1024) NOT NULL,
    "thumbnail_url"     VARCHAR(1024),
    "file_size"         BIGINT        NOT NULL DEFAULT 0,
    "mime_type"         VARCHAR(128),
    "original_filename" VARCHAR(255),
    "width"             INTEGER,
    "height"            INTEGER
);

CREATE OR REPLACE FUNCTION "refresh_modification_time"()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW."modification_time" = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS "trg_conversation_refresh_modification_time" ON "conversation";
CREATE TRIGGER "trg_conversation_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_message_refresh_modification_time" ON "conversation_message";
CREATE TRIGGER "trg_conversation_message_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_message"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_round_refresh_modification_time" ON "conversation_round";
CREATE TRIGGER "trg_conversation_round_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_round"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_turn_refresh_modification_time" ON "conversation_turn";
CREATE TRIGGER "trg_conversation_turn_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_turn"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_llm_call_refresh_modification_time" ON "conversation_llm_call";
CREATE TRIGGER "trg_conversation_llm_call_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_llm_call"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_llm_request_message_refresh_modification_time" ON "conversation_llm_request_message";
CREATE TRIGGER "trg_llm_request_message_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_llm_request_message"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_request_message_tool_call_refresh_modification_time" ON "conversation_llm_request_message_tool_call";
CREATE TRIGGER "trg_request_message_tool_call_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_llm_request_message_tool_call"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_llm_tool_definition_refresh_modification_time" ON "conversation_llm_tool_definition";
CREATE TRIGGER "trg_llm_tool_definition_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_llm_tool_definition"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_response_tool_call_refresh_modification_time" ON "conversation_llm_response_tool_call";
CREATE TRIGGER "trg_response_tool_call_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_llm_response_tool_call"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_tool_call_execution_refresh_modification_time" ON "conversation_tool_call_execution";
CREATE TRIGGER "trg_tool_call_execution_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_tool_call_execution"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_group_refresh_modification_time" ON "conversation_group";
CREATE TRIGGER "trg_conversation_group_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_group"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_group_relation_refresh_modification_time" ON "conversation_group_relation";
CREATE TRIGGER "trg_conversation_group_relation_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_group_relation"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_sharing_refresh_modification_time" ON "conversation_sharing";
CREATE TRIGGER "trg_conversation_sharing_refresh_modification_time"
    BEFORE UPDATE
    ON "conversation_sharing"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_message_file_refresh_modification_time" ON "message_file";
CREATE TRIGGER "trg_message_file_refresh_modification_time"
    BEFORE UPDATE
    ON "message_file"
    FOR EACH ROW
EXECUTE FUNCTION "refresh_modification_time"();
