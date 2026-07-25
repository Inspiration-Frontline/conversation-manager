BEGIN;

CREATE TABLE IF NOT EXISTS "conversation_round_reference"
(
    "id" BIGSERIAL PRIMARY KEY,
    "creator_id" BIGINT NOT NULL,
    "creation_time" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "modifier_id" BIGINT NOT NULL,
    "modification_time" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "round_id" BIGINT NOT NULL,
    "source_conversation_id" VARCHAR(64) NOT NULL,
    "source_end_round_number" BIGINT NOT NULL CHECK ("source_end_round_number" > 0),
    "source_title" VARCHAR(200) NOT NULL,
    "reference_order" INTEGER NOT NULL CHECK ("reference_order" >= 0),
    CONSTRAINT "uq_round_reference_source" UNIQUE ("round_id", "source_conversation_id"),
    CONSTRAINT "uq_round_reference_order" UNIQUE ("round_id", "reference_order")
);

CREATE INDEX IF NOT EXISTS "idx_round_reference_source"
    ON "conversation_round_reference" ("creator_id", "source_conversation_id", "source_end_round_number");

COMMIT;
