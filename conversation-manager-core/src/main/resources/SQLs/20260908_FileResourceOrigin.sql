SET TIME ZONE 'UTC';

ALTER TABLE "file_resource"
    ADD COLUMN IF NOT EXISTS "origin" VARCHAR(24) NOT NULL DEFAULT 'USER_UPLOAD';

ALTER TABLE "file_resource"
    DROP CONSTRAINT IF EXISTS "ck_file_resource_origin";

ALTER TABLE "file_resource"
    ADD CONSTRAINT "ck_file_resource_origin" CHECK ("origin" IN ('USER_UPLOAD', 'GENERATED'));
