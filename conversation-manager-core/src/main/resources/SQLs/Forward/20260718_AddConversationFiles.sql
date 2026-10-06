SET TIME ZONE 'UTC';

CREATE TABLE IF NOT EXISTS "file_resource"
(
    "id"                        BIGSERIAL PRIMARY KEY,
    "creator_id"                BIGINT        NOT NULL,
    "modifier_id"               BIGINT        NOT NULL,
    "file_id"                   VARCHAR(64)   NOT NULL,
    "kind"                      VARCHAR(32)   NOT NULL,
    "status"                    VARCHAR(32)   NOT NULL,
    "status_revision"           BIGINT        NOT NULL DEFAULT 1,
    "bucket_name"               VARCHAR(128)  NOT NULL,
    "object_key"                VARCHAR(1024) NOT NULL,
    "original_filename"         VARCHAR(255)  NOT NULL,
    "file_extension"            VARCHAR(32)   NOT NULL,
    "declared_mime_type"        VARCHAR(128)  NOT NULL,
    "detected_mime_type"        VARCHAR(128),
    "file_size"                 BIGINT        NOT NULL,
    "sha256"                    CHAR(64),
    "extracted_text"            TEXT,
    "extraction_metadata"       JSONB,
    "extraction_truncated"      BOOLEAN       NOT NULL DEFAULT FALSE,
    "width"                     INTEGER,
    "height"                    INTEGER,
    "error_code"                VARCHAR(100)  NOT NULL DEFAULT '',
    "error_message"             TEXT          NOT NULL DEFAULT '',
    "upload_expires_at"         TIMESTAMPTZ   NOT NULL,
    "confirmed_time"            TIMESTAMPTZ,
    "ready_time"                TIMESTAMPTZ,
    "orphaned_time"             TIMESTAMPTZ,
    "reserved_conversation_id"  VARCHAR(64),
    "reserved_request_id"       VARCHAR(100),
    "reserved_until"            TIMESTAMPTZ,
    "deleted"                   BOOLEAN       NOT NULL DEFAULT FALSE,
    "creation_time"             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    "modification_time"         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_file_resource_file_id" UNIQUE ("file_id"),
    CONSTRAINT "uk_file_resource_object" UNIQUE ("bucket_name", "object_key"),
    CONSTRAINT "ck_file_resource_size" CHECK ("file_size" > 0),
    CONSTRAINT "ck_file_resource_revision" CHECK ("status_revision" > 0),
    CONSTRAINT "ck_file_resource_status" CHECK ("status" IN (
        'PENDING_UPLOAD', 'VALIDATING', 'PROCESSING', 'READY', 'FAILED', 'CANCELLED',
        'DELETE_REQUESTED', 'DELETED', 'EXPIRED'
    )),
    CONSTRAINT "ck_file_resource_kind" CHECK ("kind" IN (
        'DOCUMENT', 'IMAGE', 'TEXT', 'SPREADSHEET', 'PRESENTATION'
    )),
    CONSTRAINT "ck_file_resource_hash" CHECK ("sha256" IS NULL OR "sha256" ~ '^[0-9a-f]{64}$'),
    CONSTRAINT "ck_file_resource_reservation" CHECK (
        ("reserved_conversation_id" IS NULL AND "reserved_request_id" IS NULL AND "reserved_until" IS NULL)
        OR
        (NULLIF(BTRIM("reserved_conversation_id"), '') IS NOT NULL
            AND NULLIF(BTRIM("reserved_request_id"), '') IS NOT NULL
            AND "reserved_until" IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS "idx_file_resource_owner_status"
    ON "file_resource" ("creator_id", "status", "creation_time" DESC)
    WHERE "deleted" = FALSE;

CREATE INDEX IF NOT EXISTS "idx_file_resource_orphan_cleanup"
    ON "file_resource" ("orphaned_time")
    WHERE "deleted" = FALSE AND "orphaned_time" IS NOT NULL;

CREATE TABLE IF NOT EXISTS "file_processing_task"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT       NOT NULL,
    "modifier_id"       BIGINT       NOT NULL,
    "file_resource_id"  BIGINT       NOT NULL,
    "status"            VARCHAR(16)  NOT NULL,
    "attempt"           INTEGER      NOT NULL DEFAULT 0,
    "execute_after"     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "lease_token"       VARCHAR(100),
    "lease_until"       TIMESTAMPTZ,
    "last_error"        TEXT         NOT NULL DEFAULT '',
    "creation_time"     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time" TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_file_processing_task_resource" UNIQUE ("file_resource_id"),
    CONSTRAINT "ck_file_processing_task_status" CHECK ("status" IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT "ck_file_processing_task_attempt" CHECK ("attempt" >= 0),
    CONSTRAINT "ck_file_processing_task_lease" CHECK (
        ("status" = 'RUNNING' AND NULLIF(BTRIM("lease_token"), '') IS NOT NULL AND "lease_until" IS NOT NULL)
        OR
        ("status" != 'RUNNING' AND "lease_token" IS NULL AND "lease_until" IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS "idx_file_processing_task_claim"
    ON "file_processing_task" ("status", "execute_after", "id");

CREATE TABLE IF NOT EXISTS "file_cleanup_task"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT       NOT NULL,
    "modifier_id"       BIGINT       NOT NULL,
    "file_resource_id"  BIGINT       NOT NULL,
    "reason"            VARCHAR(32)  NOT NULL,
    "status"            VARCHAR(16)  NOT NULL,
    "attempt"           INTEGER      NOT NULL DEFAULT 0,
    "execute_after"     TIMESTAMPTZ  NOT NULL,
    "lease_token"       VARCHAR(100),
    "lease_until"       TIMESTAMPTZ,
    "last_error"        TEXT         NOT NULL DEFAULT '',
    "creation_time"     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modification_time" TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_file_cleanup_task_resource" UNIQUE ("file_resource_id"),
    CONSTRAINT "ck_file_cleanup_task_reason" CHECK ("reason" IN (
        'UPLOAD_EXPIRED', 'USER_REMOVED', 'ORPHANED', 'CONVERSATION_DELETED'
    )),
    CONSTRAINT "ck_file_cleanup_task_status" CHECK ("status" IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT "ck_file_cleanup_task_attempt" CHECK ("attempt" >= 0),
    CONSTRAINT "ck_file_cleanup_task_lease" CHECK (
        ("status" = 'RUNNING' AND NULLIF(BTRIM("lease_token"), '') IS NOT NULL AND "lease_until" IS NOT NULL)
        OR
        ("status" != 'RUNNING' AND "lease_token" IS NULL AND "lease_until" IS NULL)
    )
);

ALTER TABLE "file_cleanup_task" DROP CONSTRAINT IF EXISTS "ck_file_cleanup_task_reason";
ALTER TABLE "file_cleanup_task" ADD CONSTRAINT "ck_file_cleanup_task_reason" CHECK (
    "reason" IN ('UPLOAD_EXPIRED', 'USER_REMOVED', 'ORPHANED', 'CONVERSATION_DELETED')
);

CREATE INDEX IF NOT EXISTS "idx_file_cleanup_task_claim"
    ON "file_cleanup_task" ("status", "execute_after", "id");

CREATE TABLE IF NOT EXISTS "conversation_round_file"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT      NOT NULL,
    "modifier_id"       BIGINT      NOT NULL,
    "round_id"          BIGINT      NOT NULL,
    "file_resource_id"  BIGINT      NOT NULL,
    "file_order"        INTEGER     NOT NULL,
    "creation_time"     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "modification_time" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_conversation_round_file_order" UNIQUE ("round_id", "file_order"),
    CONSTRAINT "uk_conversation_round_file_resource" UNIQUE ("round_id", "file_resource_id"),
    CONSTRAINT "ck_conversation_round_file_order" CHECK ("file_order" >= 0)
);

CREATE INDEX IF NOT EXISTS "idx_conversation_round_file_resource"
    ON "conversation_round_file" ("file_resource_id");

DROP TRIGGER IF EXISTS "trg_file_resource_refresh_modification_time" ON "file_resource";
CREATE TRIGGER "trg_file_resource_refresh_modification_time"
    BEFORE UPDATE ON "file_resource"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_file_processing_task_refresh_modification_time" ON "file_processing_task";
CREATE TRIGGER "trg_file_processing_task_refresh_modification_time"
    BEFORE UPDATE ON "file_processing_task"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_file_cleanup_task_refresh_modification_time" ON "file_cleanup_task";
CREATE TRIGGER "trg_file_cleanup_task_refresh_modification_time"
    BEFORE UPDATE ON "file_cleanup_task"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_round_file_refresh_modification_time" ON "conversation_round_file";
CREATE TRIGGER "trg_conversation_round_file_refresh_modification_time"
    BEFORE UPDATE ON "conversation_round_file"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();
