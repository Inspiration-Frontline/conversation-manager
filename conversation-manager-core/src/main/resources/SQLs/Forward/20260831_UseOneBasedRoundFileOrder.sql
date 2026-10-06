DO $$
DECLARE
    order_constraint TEXT;
BEGIN
    SELECT PG_GET_CONSTRAINTDEF(constraint_row."oid")
    INTO order_constraint
    FROM "pg_constraint" constraint_row
    WHERE constraint_row."conrelid" = 'conversation_round_file'::REGCLASS
      AND constraint_row."conname" = 'ck_conversation_round_file_order';

    IF order_constraint LIKE '%file_order >= 1%' THEN
        RETURN;
    END IF;

    IF order_constraint IS NULL OR order_constraint NOT LIKE '%file_order >= 0%' THEN
        RAISE EXCEPTION 'Unexpected conversation_round_file order constraint: %', order_constraint;
    END IF;

    ALTER TABLE "conversation_round_file"
        DROP CONSTRAINT "ck_conversation_round_file_order";

    UPDATE "conversation_round_file"
    SET "file_order" = -"file_order" - 1;

    UPDATE "conversation_round_file"
    SET "file_order" = -"file_order";

    ALTER TABLE "conversation_round_file"
        ADD CONSTRAINT "ck_conversation_round_file_order" CHECK ("file_order" >= 1);
END;
$$;
