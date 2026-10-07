SET TIME ZONE 'UTC';

ALTER TABLE "conversation_round_input_file"
    RENAME CONSTRAINT "conversation_round_input_file_pkey" TO "conversation_round_file_pkey";
ALTER TABLE "conversation_round_input_file"
    RENAME CONSTRAINT "uk_conversation_round_input_file_order" TO "uk_conversation_round_file_order";
ALTER TABLE "conversation_round_input_file"
    RENAME CONSTRAINT "uk_conversation_round_input_file_resource" TO "uk_conversation_round_file_resource";
ALTER TABLE "conversation_round_input_file"
    RENAME CONSTRAINT "ck_conversation_round_input_file_order" TO "ck_conversation_round_file_order";
ALTER INDEX "idx_conversation_round_input_file_resource"
    RENAME TO "idx_conversation_round_file_resource";
ALTER TRIGGER "trg_conversation_round_input_file_refresh_modification_time" ON "conversation_round_input_file"
    RENAME TO "trg_conversation_round_file_refresh_modification_time";
ALTER TABLE "conversation_round_input_file" RENAME TO "conversation_round_file";
ALTER SEQUENCE "conversation_round_input_file_id_seq" RENAME TO "conversation_round_file_id_seq";

CREATE OR REPLACE FUNCTION fork_conversation_history(
    p_source_conversation_id VARCHAR,
    p_target_conversation_id VARCHAR,
    p_user_id BIGINT,
    p_end_round_number BIGINT
) RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    copied_rounds INTEGER;
BEGIN
    copied_rounds := fork_conversation_history_without_files(
        p_source_conversation_id,
        p_target_conversation_id,
        p_user_id,
        p_end_round_number
    );

    INSERT INTO "conversation_round_file" (
        "creator_id", "modifier_id", "round_id", "file_resource_id", "file_order"
    )
    SELECT p_user_id, p_user_id, target_round."id",
           source_file."file_resource_id", source_file."file_order"
    FROM "conversation_round" source_round
    INNER JOIN "conversation_round" target_round
        ON target_round."conversation_id" = p_target_conversation_id
       AND target_round."round_number" = source_round."round_number"
    INNER JOIN "conversation_round_file" source_file
        ON source_file."round_id" = source_round."id"
    WHERE source_round."conversation_id" = p_source_conversation_id
      AND source_round."round_number" <= p_end_round_number
      AND source_round."status" = 'COMPLETED'
      AND source_round."deleted" = FALSE
    ON CONFLICT DO NOTHING;

    INSERT INTO "conversation_round_generated_file" (
        "creator_id", "modifier_id", "round_id", "file_resource_id", "source_turn_number",
        "output_order", "generation_attempt_id", "output_kind", "output_status"
    )
    SELECT p_user_id, p_user_id, target_round."id", source_generated."file_resource_id",
           source_generated."source_turn_number", source_generated."output_order",
           source_generated."generation_attempt_id", source_generated."output_kind",
           source_generated."output_status"
    FROM "conversation_round" source_round
    INNER JOIN "conversation_round" target_round
        ON target_round."conversation_id" = p_target_conversation_id
       AND target_round."round_number" = source_round."round_number"
    INNER JOIN "conversation_round_generated_file" source_generated
        ON source_generated."round_id" = source_round."id"
    WHERE source_round."conversation_id" = p_source_conversation_id
      AND source_round."round_number" <= p_end_round_number
      AND source_round."status" = 'COMPLETED'
      AND source_round."deleted" = FALSE
    ON CONFLICT DO NOTHING;

    INSERT INTO "conversation_round_edit_source" (
        "creator_id", "modifier_id", "round_id", "source_file_resource_id", "source_kind",
        "source_round_id", "resolution_kind", "resolver_execution_id", "resolution_reason"
    )
    SELECT p_user_id, p_user_id, target_round."id", source_edit."source_file_resource_id",
           source_edit."source_kind", target_source_round."id", source_edit."resolution_kind",
           source_edit."resolver_execution_id", source_edit."resolution_reason"
    FROM "conversation_round" source_round
    INNER JOIN "conversation_round" target_round
        ON target_round."conversation_id" = p_target_conversation_id
       AND target_round."round_number" = source_round."round_number"
    INNER JOIN "conversation_round_edit_source" source_edit
        ON source_edit."round_id" = source_round."id"
    LEFT JOIN "conversation_round" source_source_round
        ON source_source_round."id" = source_edit."source_round_id"
    LEFT JOIN "conversation_round" target_source_round
        ON target_source_round."conversation_id" = p_target_conversation_id
       AND target_source_round."round_number" = source_source_round."round_number"
    WHERE source_round."conversation_id" = p_source_conversation_id
      AND source_round."round_number" <= p_end_round_number
      AND source_round."status" = 'COMPLETED'
      AND source_round."deleted" = FALSE
    ON CONFLICT DO NOTHING;

    RETURN copied_rounds;
END;
$$;
