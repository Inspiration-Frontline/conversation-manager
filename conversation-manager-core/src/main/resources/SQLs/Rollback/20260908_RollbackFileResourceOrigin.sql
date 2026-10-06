ALTER TABLE "file_resource" DROP CONSTRAINT IF EXISTS "ck_file_resource_origin";
ALTER TABLE "file_resource" DROP COLUMN IF EXISTS "origin";
