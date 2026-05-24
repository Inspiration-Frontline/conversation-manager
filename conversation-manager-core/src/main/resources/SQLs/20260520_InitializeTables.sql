CREATE TABLE IF NOT EXISTS "conversation"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT       NOT NULL,
    "creation_time"     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "modifier_id"       BIGINT       NOT NULL,
    "modification_time" TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "conversation_id"   VARCHAR(64)  NOT NULL,
    "title"             VARCHAR(200) NOT NULL,
    "deleted"           BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT "uk_conversation_conversation_id" UNIQUE ("conversation_id")
);

CREATE INDEX IF NOT EXISTS "idx_conversation_creator_deleted_modified"
    ON "conversation" ("creator_id", "deleted", "modification_time" DESC);

CREATE INDEX IF NOT EXISTS "idx_conversation_title"
    ON "conversation" ("title");

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
    "deleted"           BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT "fk_conversation_message_conversation" FOREIGN KEY ("conversation_id") REFERENCES "conversation" ("conversation_id") ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS "idx_conversation_message_conversation_id"
    ON "conversation_message" ("conversation_id", "id");

CREATE INDEX IF NOT EXISTS "idx_conversation_message_deleted"
    ON "conversation_message" ("conversation_id", "deleted");

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
    "conversation_group_id" VARCHAR(64),
    "sort_order"            INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT "fk_conversation_group_relation_conversation" FOREIGN KEY ("conversation_id") REFERENCES "conversation" ("conversation_id") ON DELETE CASCADE,
    CONSTRAINT "fk_conversation_group_relation_group" FOREIGN KEY ("conversation_group_id") REFERENCES "conversation_group" ("group_id") ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_conversation_group_relation_group"
    ON "conversation_group_relation" ("creator_id", "conversation_group_id", "conversation_id")
    WHERE "conversation_group_id" IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS "uk_conversation_group_relation_pin"
    ON "conversation_group_relation" ("creator_id", "conversation_id")
    WHERE "conversation_group_id" IS NULL;

CREATE INDEX IF NOT EXISTS "idx_conversation_group_relation_group_sort"
    ON "conversation_group_relation" ("creator_id", "conversation_group_id", "sort_order", "id");

CREATE TABLE IF NOT EXISTS "conversation_sharing"
(
    "id"                         BIGSERIAL PRIMARY KEY,
    "creator_id"                 BIGINT      NOT NULL,
    "creation_time"              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "modifier_id"                BIGINT      NOT NULL,
    "modification_time"          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "parent_conversation_id"     VARCHAR(64) NOT NULL,
    "shared_conversation_id"     VARCHAR(64) NOT NULL,
    "end_message_id"             BIGINT      NOT NULL DEFAULT 0,
    "accessible_after_deleted"   BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT "fk_conversation_sharing_parent" FOREIGN KEY ("parent_conversation_id") REFERENCES "conversation" ("conversation_id") ON DELETE CASCADE,
    CONSTRAINT "uk_conversation_sharing_shared_id" UNIQUE ("shared_conversation_id")
);

CREATE INDEX IF NOT EXISTS "idx_conversation_sharing_parent"
    ON "conversation_sharing" ("parent_conversation_id");

CREATE TABLE IF NOT EXISTS "message_file"
(
    "id"                BIGSERIAL PRIMARY KEY,
    "creator_id"        BIGINT      NOT NULL,
    "creation_time"     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "modifier_id"       BIGINT      NOT NULL,
    "modification_time" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "file_type"         VARCHAR(32) NOT NULL,
    "oss_url"           VARCHAR(1024) NOT NULL,
    "thumbnail_url"     VARCHAR(1024),
    "file_size"         BIGINT      NOT NULL DEFAULT 0,
    "mime_type"         VARCHAR(128),
    "original_filename" VARCHAR(255),
    "width"             INTEGER,
    "height"            INTEGER
);

CREATE OR REPLACE FUNCTION "refresh_modification_time"()
RETURNS TRIGGER AS $$
BEGIN
    NEW."modification_time" = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS "trg_conversation_refresh_modification_time" ON "conversation";
CREATE TRIGGER "trg_conversation_refresh_modification_time"
    BEFORE UPDATE ON "conversation"
    FOR EACH ROW
    EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_message_refresh_modification_time" ON "conversation_message";
CREATE TRIGGER "trg_conversation_message_refresh_modification_time"
    BEFORE UPDATE ON "conversation_message"
    FOR EACH ROW
    EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_group_refresh_modification_time" ON "conversation_group";
CREATE TRIGGER "trg_conversation_group_refresh_modification_time"
    BEFORE UPDATE ON "conversation_group"
    FOR EACH ROW
    EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_group_relation_refresh_modification_time" ON "conversation_group_relation";
CREATE TRIGGER "trg_conversation_group_relation_refresh_modification_time"
    BEFORE UPDATE ON "conversation_group_relation"
    FOR EACH ROW
    EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_conversation_sharing_refresh_modification_time" ON "conversation_sharing";
CREATE TRIGGER "trg_conversation_sharing_refresh_modification_time"
    BEFORE UPDATE ON "conversation_sharing"
    FOR EACH ROW
    EXECUTE FUNCTION "refresh_modification_time"();

DROP TRIGGER IF EXISTS "trg_message_file_refresh_modification_time" ON "message_file";
CREATE TRIGGER "trg_message_file_refresh_modification_time"
    BEFORE UPDATE ON "message_file"
    FOR EACH ROW
    EXECUTE FUNCTION "refresh_modification_time"();
