-- CreateTable
CREATE TABLE IF NOT EXISTS "system_settings" (
    "key" TEXT NOT NULL,
    "value" TEXT NOT NULL,
    "is_secret" BOOLEAN NOT NULL DEFAULT false,
    "description" TEXT NOT NULL DEFAULT '',
    "updated_by" TEXT,
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "system_settings_pkey" PRIMARY KEY ("key")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "customer_numbers" (
    "id" TEXT NOT NULL,
    "user_id" TEXT NOT NULL,
    "phone_hash" TEXT NOT NULL,
    "encrypted_normalized_phone" TEXT NOT NULL,
    "encrypted_display_phone" TEXT NOT NULL,
    "encrypted_nickname" TEXT,
    "first_used_at" TIMESTAMP(3) NOT NULL,
    "last_used_at" TIMESTAMP(3) NOT NULL,
    "usage_count" INTEGER NOT NULL DEFAULT 1,
    "version" INTEGER NOT NULL DEFAULT 1,
    "deleted_at" TIMESTAMP(3),
    "synced_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "customer_numbers_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX IF NOT EXISTS "customer_numbers_user_id_phone_hash_key" ON "customer_numbers"("user_id", "phone_hash");
CREATE INDEX IF NOT EXISTS "customer_numbers_user_id_updated_at_idx" ON "customer_numbers"("user_id", "updated_at");
CREATE INDEX IF NOT EXISTS "customer_numbers_user_id_deleted_at_idx" ON "customer_numbers"("user_id", "deleted_at");

-- AddForeignKey
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'customer_numbers_user_id_fkey'
    ) THEN
        ALTER TABLE "customer_numbers"
            ADD CONSTRAINT "customer_numbers_user_id_fkey"
            FOREIGN KEY ("user_id") REFERENCES "users"("id")
            ON DELETE CASCADE ON UPDATE CASCADE;
    END IF;
END $$;
