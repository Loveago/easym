-- CreateEnum
CREATE TYPE "UserRole" AS ENUM ('AGENT', 'ADMIN');
CREATE TYPE "AccountStatus" AS ENUM ('ACTIVE', 'SUSPENDED');
CREATE TYPE "EntitlementStatus" AS ENUM ('TRIAL', 'ACTIVE', 'EXPIRED', 'CANCELLED');
CREATE TYPE "BillingInterval" AS ENUM ('MONTHLY', 'QUARTERLY', 'ANNUAL');
CREATE TYPE "PaymentStatus" AS ENUM ('PENDING', 'SUCCESS', 'FAILED');
CREATE TYPE "NetworkType" AS ENUM ('MTN', 'AT', 'TELECEL');
CREATE TYPE "TransactionType" AS ENUM ('CASH_IN', 'CASH_OUT', 'AIRTIME', 'DATA', 'MERCHANT_PAY', 'BALANCE', 'COMMISSION', 'CUSTOM_USSD');
CREATE TYPE "ProcessingType" AS ENUM ('AUTOMATED_USSD', 'CUSTOMER_LED', 'MANUAL_USSD');
CREATE TYPE "TransactionStatus" AS ENUM ('DRAFT', 'PROCESSING', 'AWAITING_CUSTOMER', 'AWAITING_VERIFICATION', 'SUCCESSFUL', 'FAILED', 'CANCELLED', 'UNKNOWN');

-- CreateTable
CREATE TABLE "users" (
    "id" TEXT NOT NULL,
    "full_name" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "phone" TEXT NOT NULL,
    "password_hash" TEXT NOT NULL,
    "role" "UserRole" NOT NULL DEFAULT 'AGENT',
    "account_status" "AccountStatus" NOT NULL DEFAULT 'ACTIVE',
    "trial_consumed" BOOLEAN NOT NULL DEFAULT false,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "subscription_plans" (
    "id" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "price" DECIMAL(12,2) NOT NULL,
    "currency" TEXT NOT NULL DEFAULT 'GHS',
    "billing_interval" "BillingInterval" NOT NULL,
    "duration_days" INTEGER NOT NULL,
    "active" BOOLEAN NOT NULL DEFAULT true,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "subscription_plans_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "subscription_entitlements" (
    "id" TEXT NOT NULL,
    "user_id" TEXT NOT NULL,
    "status" "EntitlementStatus" NOT NULL DEFAULT 'TRIAL',
    "trial_started_at" TIMESTAMP(3),
    "trial_expires_at" TIMESTAMP(3),
    "paid_until" TIMESTAMP(3),
    "current_plan_id" TEXT,
    "recurring_authorized" BOOLEAN NOT NULL DEFAULT false,
    "authorization_code" TEXT,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "subscription_entitlements_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "payments" (
    "id" TEXT NOT NULL,
    "user_id" TEXT NOT NULL,
    "plan_id" TEXT NOT NULL,
    "provider" TEXT NOT NULL DEFAULT 'PAYSTACK',
    "reference" TEXT NOT NULL,
    "amount" DECIMAL(12,2) NOT NULL,
    "currency" TEXT NOT NULL DEFAULT 'GHS',
    "channel" TEXT,
    "status" "PaymentStatus" NOT NULL DEFAULT 'PENDING',
    "authorization_reusable" BOOLEAN NOT NULL DEFAULT false,
    "paid_at" TIMESTAMP(3),
    "raw_metadata" JSONB,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "payments_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "data_bundles" (
    "id" TEXT NOT NULL,
    "network" "NetworkType" NOT NULL,
    "name" TEXT NOT NULL,
    "allowance" TEXT NOT NULL,
    "price" DECIMAL(12,2) NOT NULL,
    "validity" TEXT NOT NULL,
    "provider_option" TEXT NOT NULL,
    "active" BOOLEAN NOT NULL DEFAULT true,
    "last_verified_at" TIMESTAMP(3),
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "data_bundles_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "transactions" (
    "id" TEXT NOT NULL,
    "user_id" TEXT NOT NULL,
    "client_transaction_id" TEXT NOT NULL,
    "provider_reference" TEXT,
    "network" "NetworkType" NOT NULL,
    "transaction_type" "TransactionType" NOT NULL,
    "processing_type" "ProcessingType" NOT NULL,
    "recipient_number" TEXT NOT NULL,
    "amount" DECIMAL(12,2) NOT NULL,
    "bundle_id" TEXT,
    "bundle_name" TEXT,
    "status" "TransactionStatus" NOT NULL,
    "confirmation_source" TEXT,
    "provider_response" TEXT,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "transactions_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "transaction_events" (
    "id" TEXT NOT NULL,
    "transaction_id" TEXT NOT NULL,
    "event_type" TEXT NOT NULL,
    "previous_status" "TransactionStatus",
    "new_status" "TransactionStatus" NOT NULL,
    "details" TEXT,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "transaction_events_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "ussd_flow_profiles" (
    "id" TEXT NOT NULL,
    "network" "NetworkType" NOT NULL,
    "transaction_type" TEXT NOT NULL,
    "version" INTEGER NOT NULL,
    "configuration" JSONB NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT false,
    "last_verified_at" TIMESTAMP(3),
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "ussd_flow_profiles_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "network_profiles" (
    "id" TEXT NOT NULL,
    "network" "NetworkType" NOT NULL,
    "configuration" JSONB NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 1,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "network_profiles_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "users_email_key" ON "users"("email");
CREATE UNIQUE INDEX "users_phone_key" ON "users"("phone");
CREATE INDEX "users_email_idx" ON "users"("email");
CREATE INDEX "users_phone_idx" ON "users"("phone");

CREATE UNIQUE INDEX "subscription_plans_code_key" ON "subscription_plans"("code");
CREATE UNIQUE INDEX "subscription_entitlements_user_id_key" ON "subscription_entitlements"("user_id");
CREATE INDEX "subscription_entitlements_status_idx" ON "subscription_entitlements"("status");

CREATE UNIQUE INDEX "payments_reference_key" ON "payments"("reference");
CREATE INDEX "payments_user_id_created_at_idx" ON "payments"("user_id", "created_at");
CREATE INDEX "payments_reference_idx" ON "payments"("reference");

CREATE INDEX "data_bundles_network_active_idx" ON "data_bundles"("network", "active");

CREATE UNIQUE INDEX "transactions_user_id_client_transaction_id_key" ON "transactions"("user_id", "client_transaction_id");
CREATE INDEX "transactions_user_id_created_at_idx" ON "transactions"("user_id", "created_at");
CREATE INDEX "transactions_user_id_network_transaction_type_status_idx" ON "transactions"("user_id", "network", "transaction_type", "status");

CREATE INDEX "transaction_events_transaction_id_created_at_idx" ON "transaction_events"("transaction_id", "created_at");

CREATE UNIQUE INDEX "ussd_flow_profiles_network_transaction_type_version_key" ON "ussd_flow_profiles"("network", "transaction_type", "version");
CREATE INDEX "ussd_flow_profiles_network_transaction_type_enabled_idx" ON "ussd_flow_profiles"("network", "transaction_type", "enabled");

CREATE UNIQUE INDEX "network_profiles_network_key" ON "network_profiles"("network");

-- AddForeignKey
ALTER TABLE "subscription_entitlements" ADD CONSTRAINT "subscription_entitlements_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "subscription_entitlements" ADD CONSTRAINT "subscription_entitlements_current_plan_id_fkey" FOREIGN KEY ("current_plan_id") REFERENCES "subscription_plans"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "payments" ADD CONSTRAINT "payments_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "payments" ADD CONSTRAINT "payments_plan_id_fkey" FOREIGN KEY ("plan_id") REFERENCES "subscription_plans"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "transactions" ADD CONSTRAINT "transactions_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "transactions" ADD CONSTRAINT "transactions_bundle_id_fkey" FOREIGN KEY ("bundle_id") REFERENCES "data_bundles"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "transaction_events" ADD CONSTRAINT "transaction_events_transaction_id_fkey" FOREIGN KEY ("transaction_id") REFERENCES "transactions"("id") ON DELETE CASCADE ON UPDATE CASCADE;
