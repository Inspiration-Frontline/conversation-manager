ALTER TABLE "conversation_round_mutation"
    ADD COLUMN "creator_id" BIGINT,
    ADD COLUMN "modifier_id" BIGINT,
    ADD COLUMN "modification_time" TIMESTAMPTZ;

UPDATE "conversation_round_mutation" mutation
SET "creator_id" = round."creator_id",
    "modifier_id" = round."modifier_id",
    "modification_time" = mutation."creation_time"
FROM "conversation_round" round
WHERE round."id" = mutation."round_id";

ALTER TABLE "conversation_round_mutation"
    ALTER COLUMN "creator_id" SET NOT NULL,
    ALTER COLUMN "modifier_id" SET NOT NULL,
    ALTER COLUMN "modification_time" SET NOT NULL;

ALTER TABLE "conversation_tool_dispatch"
    ADD COLUMN "creator_id" BIGINT,
    ADD COLUMN "modifier_id" BIGINT;

UPDATE "conversation_tool_dispatch" dispatch
SET "creator_id" = round."creator_id",
    "modifier_id" = round."modifier_id"
FROM "conversation_round" round
WHERE round."id" = dispatch."round_id";

ALTER TABLE "conversation_tool_dispatch"
    ALTER COLUMN "creator_id" SET NOT NULL,
    ALTER COLUMN "modifier_id" SET NOT NULL;

DROP TRIGGER IF EXISTS "trg_round_mutation_refresh_modification_time" ON "conversation_round_mutation";
CREATE TRIGGER "trg_round_mutation_refresh_modification_time"
    BEFORE UPDATE ON "conversation_round_mutation"
    FOR EACH ROW EXECUTE FUNCTION "refresh_modification_time"();
