DROP TRIGGER IF EXISTS "trg_round_mutation_refresh_modification_time" ON "conversation_round_mutation";

ALTER TABLE "conversation_tool_dispatch"
    DROP COLUMN "modifier_id",
    DROP COLUMN "creator_id";

ALTER TABLE "conversation_round_mutation"
    DROP COLUMN "modification_time",
    DROP COLUMN "modifier_id",
    DROP COLUMN "creator_id";
