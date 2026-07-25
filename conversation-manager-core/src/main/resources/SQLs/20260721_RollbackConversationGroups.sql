SET TIME ZONE 'UTC';

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

INSERT INTO "conversation_group_relation" (
    "creator_id", "modifier_id", "conversation_id", "conversation_group_id", "sort_order"
)
SELECT "creator_id", "modifier_id", "conversation_id", "conversation_group_id",
       ROW_NUMBER() OVER (
           PARTITION BY "creator_id", "conversation_group_id"
           ORDER BY "last_round_updated_time" DESC, "id" DESC
       )
FROM "conversation"
WHERE "conversation_group_id" IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS "idx_conversation_group_relation_group_sort"
    ON "conversation_group_relation" ("conversation_group_id", "sort_order" DESC, "id" DESC);

CREATE INDEX IF NOT EXISTS "idx_conversation_group_relation_conversation"
    ON "conversation_group_relation" ("creator_id", "conversation_id");

DROP INDEX IF EXISTS "idx_conversation_title_search";
DROP INDEX IF EXISTS "idx_conversation_group_sidebar";
DROP INDEX IF EXISTS "idx_conversation_root_sidebar";

CREATE INDEX IF NOT EXISTS "idx_conversation_creator_deleted_pinned_modified"
    ON "conversation" ("creator_id", "deleted", "pinned" DESC, "modification_time" DESC);

ALTER TABLE "conversation"
    DROP COLUMN IF EXISTS "last_round_updated_time",
    DROP COLUMN IF EXISTS "conversation_group_id";

DROP TRIGGER IF EXISTS "trg_conversation_group_relation_refresh_modification_time" ON "conversation_group_relation";
CREATE TRIGGER "trg_conversation_group_relation_refresh_modification_time"
    BEFORE UPDATE ON "conversation_group_relation"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();
