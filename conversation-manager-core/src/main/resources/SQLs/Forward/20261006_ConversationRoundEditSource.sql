SET TIME ZONE 'UTC';

CREATE TABLE IF NOT EXISTS "conversation_round_edit_source"
(
    "id"                      BIGSERIAL PRIMARY KEY,
    "creator_id"              BIGINT       NOT NULL,
    "modifier_id"             BIGINT       NOT NULL,
    "round_id"                BIGINT       NOT NULL,
    "source_file_resource_id" BIGINT       NOT NULL,
    "source_kind"             VARCHAR(24)  NOT NULL,
    "source_round_id"         BIGINT,
    "resolution_kind"         VARCHAR(24)  NOT NULL,
    "resolver_execution_id"   BIGINT,
    "resolution_reason"       TEXT         NOT NULL DEFAULT '',
    "creation_time"           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time"       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_conversation_round_edit_source_round" UNIQUE ("round_id"),
    CONSTRAINT "ck_conversation_round_edit_source_kind" CHECK ("source_kind" IN ('UPLOADED', 'GENERATED')),
    CONSTRAINT "ck_conversation_round_edit_source_resolution" CHECK (
        "resolution_kind" IN ('EXPLICIT', 'REFERENCE_RESOLVER')
    ),
    CONSTRAINT "ck_conversation_round_edit_source_resolver" CHECK (
        ("resolution_kind" = 'REFERENCE_RESOLVER' AND "resolver_execution_id" IS NOT NULL)
        OR
        ("resolution_kind" = 'EXPLICIT' AND "resolver_execution_id" IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS "idx_conversation_round_edit_source_file"
    ON "conversation_round_edit_source" ("source_file_resource_id");

CREATE INDEX IF NOT EXISTS "idx_conversation_round_edit_source_source_round"
    ON "conversation_round_edit_source" ("source_round_id")
    WHERE "source_round_id" IS NOT NULL;

DROP TRIGGER IF EXISTS "trg_conversation_round_edit_source_refresh_modification_time"
    ON "conversation_round_edit_source";
CREATE TRIGGER "trg_conversation_round_edit_source_refresh_modification_time"
    BEFORE UPDATE ON "conversation_round_edit_source"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();
