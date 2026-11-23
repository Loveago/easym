-- CreateTable
CREATE TABLE IF NOT EXISTS "trial_devices" (
    "id" TEXT NOT NULL,
    "device_id" TEXT NOT NULL,
    "first_user_id" TEXT NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "trial_devices_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX IF NOT EXISTS "trial_devices_device_id_key" ON "trial_devices"("device_id");
