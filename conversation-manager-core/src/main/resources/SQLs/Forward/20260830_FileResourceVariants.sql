SET TIME ZONE 'UTC';

CREATE TABLE IF NOT EXISTS "file_resource_variant"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT        NOT NULL,
    "modifier_id"       BIGINT        NOT NULL,
    "file_resource_id"  BIGINT        NOT NULL,
    "variant_type"      VARCHAR(32)   NOT NULL,
    "status"            VARCHAR(16)   NOT NULL,
    "bucket_name"       VARCHAR(128)  NOT NULL,
    "object_key"        VARCHAR(1024) NOT NULL,
    "mime_type"         VARCHAR(128),
    "file_size"         BIGINT,
    "sha256"            CHAR(64),
    "width"             INTEGER,
    "height"            INTEGER,
    "creation_time"     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    "modification_time" TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT "uk_file_resource_variant_type" UNIQUE ("file_resource_id", "variant_type"),
    CONSTRAINT "uk_file_resource_variant_object" UNIQUE ("bucket_name", "object_key"),
    CONSTRAINT "ck_file_resource_variant_type" CHECK ("variant_type" IN ('MODEL_INPUT')),
    CONSTRAINT "ck_file_resource_variant_status" CHECK ("status" IN ('PENDING', 'READY')),
    CONSTRAINT "ck_file_resource_variant_ready" CHECK (
        "status" != 'READY' OR (
            "file_size" > 0 AND "width" > 0 AND "height" > 0
            AND "sha256" ~ '^[0-9a-f]{64}$'
            AND NULLIF(BTRIM("mime_type"), '') IS NOT NULL
        )
    )
);

CREATE INDEX IF NOT EXISTS "idx_file_resource_variant_resource_status"
    ON "file_resource_variant" ("file_resource_id", "status");

DROP TRIGGER IF EXISTS "trg_file_resource_variant_refresh_modification_time" ON "file_resource_variant";
CREATE TRIGGER "trg_file_resource_variant_refresh_modification_time"
    BEFORE UPDATE ON "file_resource_variant"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();
