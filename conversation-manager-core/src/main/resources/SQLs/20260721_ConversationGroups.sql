SET TIME ZONE 'UTC';

DO
$$
DECLARE
    has_conflicts BOOLEAN;
BEGIN
    IF to_regclass('public.conversation_group_relation') IS NOT NULL THEN
        EXECUTE '
            SELECT EXISTS (
                SELECT 1
                FROM "conversation_group_relation"
                GROUP BY "creator_id", "conversation_id"
                HAVING COUNT(DISTINCT "conversation_group_id") > 1
            )'
        INTO has_conflicts;
        IF has_conflicts THEN
            RAISE EXCEPTION 'Phase 9 migration stopped: a Conversation belongs to multiple Groups.';
        END IF;
    END IF;
END;
$$;

ALTER TABLE "conversation"
    ADD COLUMN IF NOT EXISTS "conversation_group_id" VARCHAR(64),
    ADD COLUMN IF NOT EXISTS "last_round_updated_time" TIMESTAMPTZ;

DO
$$
BEGIN
    IF to_regclass('public.conversation_group_relation') IS NOT NULL THEN
        EXECUTE '
            UPDATE "conversation" conversation
            SET "conversation_group_id" = relation."conversation_group_id"
            FROM "conversation_group_relation" relation
            WHERE relation."creator_id" = conversation."creator_id"
              AND relation."conversation_id" = conversation."conversation_id"';
    END IF;
END;
$$;

DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM "conversation" conversation
        LEFT JOIN "conversation_group" conversation_group
          ON conversation_group."creator_id" = conversation."creator_id"
         AND conversation_group."group_id" = conversation."conversation_group_id"
        WHERE conversation."conversation_group_id" IS NOT NULL
          AND conversation_group."id" IS NULL
    ) THEN
        RAISE EXCEPTION 'Phase 9 migration stopped: a Conversation references a missing or differently owned Group.';
    END IF;
END;
$$;

UPDATE "conversation" conversation
SET "last_round_updated_time" = COALESCE((
    SELECT round."creation_time"
    FROM "conversation_round" round
    WHERE round."conversation_id" = conversation."conversation_id"
    ORDER BY round."round_number" DESC
    LIMIT 1
), conversation."creation_time")
WHERE conversation."last_round_updated_time" IS NULL;

ALTER TABLE "conversation"
    ALTER COLUMN "last_round_updated_time" SET DEFAULT NOW(),
    ALTER COLUMN "last_round_updated_time" SET NOT NULL;

UPDATE "conversation"
SET "pinned" = FALSE
WHERE "conversation_group_id" IS NOT NULL
  AND "pinned" = TRUE;

DROP TABLE IF EXISTS "conversation_group_relation";

DROP INDEX IF EXISTS "idx_conversation_creator_deleted_pinned_modified";

CREATE INDEX IF NOT EXISTS "idx_conversation_root_sidebar"
    ON "conversation" ("creator_id", "pinned" DESC, "last_round_updated_time" DESC, "id" DESC)
    WHERE "deleted" = FALSE AND "conversation_group_id" IS NULL;

CREATE INDEX IF NOT EXISTS "idx_conversation_group_sidebar"
    ON "conversation" ("creator_id", "conversation_group_id", "last_round_updated_time" DESC, "id" DESC)
    WHERE "deleted" = FALSE AND "conversation_group_id" IS NOT NULL;

CREATE INDEX IF NOT EXISTS "idx_conversation_title_search"
    ON "conversation" ("creator_id", LOWER("title"))
    WHERE "deleted" = FALSE;
