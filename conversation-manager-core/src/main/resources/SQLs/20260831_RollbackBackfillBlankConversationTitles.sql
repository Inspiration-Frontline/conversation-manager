UPDATE "conversation" conversation
SET "title" = backfill."previous_title"
FROM "conversation_title_backfill" backfill
WHERE conversation."conversation_id" = backfill."conversation_id";

DROP TABLE "conversation_title_backfill";
