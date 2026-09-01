CREATE TABLE IF NOT EXISTS "conversation_title_backfill"
(
    "conversation_id" VARCHAR(64) PRIMARY KEY,
    "previous_title" VARCHAR(200) NOT NULL,
    "backfill_time"  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO "conversation_title_backfill" ("conversation_id", "previous_title")
SELECT conversation."conversation_id", conversation."title"
FROM "conversation" conversation
WHERE BTRIM(conversation."title") = ''
ON CONFLICT ("conversation_id") DO NOTHING;

WITH "derived_titles" AS
(
    SELECT backfill."conversation_id",
           LEFT(COALESCE(first_input."title", 'New Conversation'), 200) AS "title"
    FROM "conversation_title_backfill" backfill
    LEFT JOIN LATERAL
    (
        SELECT COALESCE(
            NULLIF(BTRIM(round."user_request_content"), ''),
            NULLIF(BTRIM(REGEXP_REPLACE(round_file."original_filename", '\.[^.]*$', '')), '')) AS "title"
        FROM "conversation_round" round
        LEFT JOIN LATERAL
        (
            SELECT file."original_filename"
            FROM "conversation_round_file" relation
            INNER JOIN "file_resource" file ON file."id" = relation."file_resource_id"
            WHERE relation."round_id" = round."id"
            ORDER BY relation."file_order"
            LIMIT 1
        ) round_file ON TRUE
        WHERE round."conversation_id" = backfill."conversation_id"
          AND round."deleted" = FALSE
          AND (BTRIM(round."user_request_content") <> '' OR round_file."original_filename" IS NOT NULL)
        ORDER BY round."round_number"
        LIMIT 1
    ) first_input ON TRUE
)
UPDATE "conversation" conversation
SET "title" = derived."title"
FROM "derived_titles" derived
WHERE conversation."conversation_id" = derived."conversation_id"
  AND BTRIM(conversation."title") = '';
