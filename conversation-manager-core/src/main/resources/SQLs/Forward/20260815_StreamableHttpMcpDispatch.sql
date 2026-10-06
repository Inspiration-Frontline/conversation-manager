ALTER TABLE "conversation_round"
    ADD COLUMN "revision" BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN "agent_id" BIGINT,
    ADD COLUMN "agent_name" VARCHAR(128),
    ADD COLUMN "agent_version" INTEGER,
    ADD COLUMN "mcp_server_bindings" JSONB NOT NULL DEFAULT '[]'::JSONB;

ALTER TABLE "conversation_round" ALTER COLUMN "end_time" DROP NOT NULL;
ALTER TABLE "conversation_round" DROP CONSTRAINT "ck_round_status";
ALTER TABLE "conversation_round" DROP CONSTRAINT "ck_round_time";
ALTER TABLE "conversation_round" DROP CONSTRAINT "ck_round_result";
ALTER TABLE "conversation_round"
    ADD CONSTRAINT "ck_round_revision" CHECK ("revision" >= 0),
    ADD CONSTRAINT "ck_round_status" CHECK (
        "status" IN ('COMPLETED', 'FAILED', 'CANCELLED', 'IN_PROGRESS')),
    ADD CONSTRAINT "ck_round_time" CHECK (
        ("status" = 'IN_PROGRESS' AND "end_time" IS NULL)
            OR ("status" <> 'IN_PROGRESS' AND "end_time" >= "start_time")),
    ADD CONSTRAINT "ck_round_result" CHECK (
        ("status" = 'IN_PROGRESS'
            AND "end_time" IS NULL
            AND "final_answer_content" IS NULL
            AND "final_answer_content_parts" IS NULL
            AND "final_source_turn_number" IS NULL
            AND "error_message" = '')
        OR ("status" = 'COMPLETED'
            AND "end_time" IS NOT NULL
            AND "error_message" = ''
            AND "final_source_turn_number" > 0
            AND ((NULLIF(BTRIM("final_answer_content"), '') IS NOT NULL
                    AND "final_answer_content_parts" IS NULL)
                OR ("final_answer_content" IS NULL
                    AND "final_answer_content_parts" IS NOT NULL
                    AND JSONB_TYPEOF("final_answer_content_parts") = 'array'
                    AND JSONB_ARRAY_LENGTH("final_answer_content_parts") > 0)))
        OR ("status" = 'FAILED'
            AND "end_time" IS NOT NULL
            AND NULLIF(BTRIM("error_message"), '') IS NOT NULL
            AND "final_answer_content" IS NULL
            AND "final_answer_content_parts" IS NULL
            AND "final_source_turn_number" IS NULL)
        OR ("status" = 'CANCELLED'
            AND "end_time" IS NOT NULL
            AND "final_answer_content" IS NULL
            AND "final_answer_content_parts" IS NULL
            AND "final_source_turn_number" IS NULL));

CREATE TABLE "conversation_round_mutation"
(
    "id"                 BIGSERIAL PRIMARY KEY,
    "round_id"           BIGINT      NOT NULL,
    "mutation_id"        VARCHAR(64) NOT NULL,
    "payload_hash"       CHAR(64)    NOT NULL,
    "committed_revision" BIGINT      NOT NULL,
    "creation_time"      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT "fk_round_mutation_round" FOREIGN KEY ("round_id")
        REFERENCES "conversation_round" ("id") ON DELETE CASCADE,
    CONSTRAINT "uk_round_mutation" UNIQUE ("round_id", "mutation_id"),
    CONSTRAINT "ck_round_mutation_id" CHECK (NULLIF(BTRIM("mutation_id"), '') IS NOT NULL),
    CONSTRAINT "ck_round_mutation_hash" CHECK ("payload_hash" ~ '^[0-9a-f]{64}$'),
    CONSTRAINT "ck_round_mutation_revision" CHECK ("committed_revision" >= 0)
);

CREATE TABLE "conversation_tool_dispatch"
(
    "id"                 BIGSERIAL PRIMARY KEY,
    "round_id"           BIGINT       NOT NULL,
    "attempt_id"         VARCHAR(64)  NOT NULL,
    "turn_number"        BIGINT       NOT NULL,
    "tool_call_id"       VARCHAR(200) NOT NULL,
    "tool_name"          VARCHAR(200) NOT NULL,
    "tool_key"           VARCHAR(300) NOT NULL,
    "server_id"          VARCHAR(128) NOT NULL,
    "arguments_json"     JSONB        NOT NULL,
    "state"              VARCHAR(16)  NOT NULL,
    "dispatch_time"      TIMESTAMPTZ,
    "result_time"        TIMESTAMPTZ,
    "trace_id"           CHAR(32)     NOT NULL,
    "span_id"            CHAR(16)     NOT NULL,
    "transport_evidence" TEXT         NOT NULL DEFAULT '',
    "recovery_reason"    TEXT         NOT NULL DEFAULT '',
    "creation_time"      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time"  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "fk_tool_dispatch_round" FOREIGN KEY ("round_id")
        REFERENCES "conversation_round" ("id") ON DELETE CASCADE,
    CONSTRAINT "uk_tool_dispatch_attempt" UNIQUE ("round_id", "attempt_id"),
    CONSTRAINT "uk_tool_dispatch_call" UNIQUE ("round_id", "tool_call_id"),
    CONSTRAINT "ck_tool_dispatch_turn" CHECK ("turn_number" > 0),
    CONSTRAINT "ck_tool_dispatch_identity" CHECK (
        NULLIF(BTRIM("attempt_id"), '') IS NOT NULL
        AND NULLIF(BTRIM("tool_call_id"), '') IS NOT NULL
        AND NULLIF(BTRIM("tool_name"), '') IS NOT NULL
        AND NULLIF(BTRIM("tool_key"), '') IS NOT NULL
        AND NULLIF(BTRIM("server_id"), '') IS NOT NULL),
    CONSTRAINT "ck_tool_dispatch_state" CHECK (
        "state" IN ('READY', 'DISPATCHING', 'COMPLETED', 'FAILED', 'CANCELLED', 'UNKNOWN', 'REJECTED')),
    CONSTRAINT "ck_tool_dispatch_timing" CHECK (
        ("state" = 'READY' AND "dispatch_time" IS NULL AND "result_time" IS NULL)
        OR ("state" = 'DISPATCHING' AND "dispatch_time" IS NOT NULL AND "result_time" IS NULL)
        OR ("state" IN ('COMPLETED', 'FAILED', 'CANCELLED', 'UNKNOWN', 'REJECTED')
            AND "dispatch_time" IS NOT NULL AND "result_time" >= "dispatch_time"))
);

CREATE INDEX "idx_tool_dispatch_recovery"
    ON "conversation_tool_dispatch" ("state", "dispatch_time")
    WHERE "state" = 'DISPATCHING';

CREATE TRIGGER "trg_tool_dispatch_refresh_modification_time"
    BEFORE UPDATE ON "conversation_tool_dispatch"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();
