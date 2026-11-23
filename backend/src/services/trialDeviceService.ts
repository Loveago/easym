import crypto from 'crypto';
import { PrismaClient } from '@prisma/client';
import { env } from '../config/env';
import { prisma as defaultPrisma } from '../config/prisma';

const fallbackTrialDevices = new Set<string>();
let trialTableEnsured = false;

export function clearTrialDeviceTestState(): void {
  fallbackTrialDevices.clear();
}

export async function ensureTrialDevicesTable(db: PrismaClient): Promise<void> {
  if (trialTableEnsured || env.NODE_ENV === 'test') return;
  const rawExec = (db as unknown as { $executeRawUnsafe?: (sql: string) => Promise<unknown> })
    .$executeRawUnsafe;
  if (typeof rawExec !== 'function') return;
  try {
    await rawExec.call(
      db,
      `CREATE TABLE IF NOT EXISTS "trial_devices" (
        "id" TEXT NOT NULL,
        "device_id" TEXT NOT NULL,
        "first_user_id" TEXT NOT NULL,
        "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "trial_devices_pkey" PRIMARY KEY ("id")
      )`,
    );
    await rawExec.call(
      db,
      `CREATE UNIQUE INDEX IF NOT EXISTS "trial_devices_device_id_key" ON "trial_devices"("device_id")`,
    );
    trialTableEnsured = true;
  } catch {
    // Managed by Prisma migrations
  }
}

type TrialDeviceDelegate = {
  findUnique: (args: { where: { deviceId: string }; select?: { id: true } }) => Promise<{ id: string } | null>;
  upsert: (args: {
    where: { deviceId: string };
    update: Record<string, never>;
    create: { id: string; deviceId: string; firstUserId: string };
  }) => Promise<unknown>;
};

function getDelegate(db: PrismaClient): TrialDeviceDelegate | null {
  if (db === defaultPrisma && env.NODE_ENV === 'test') {
    return null;
  }
  const candidate = (db as unknown as { trialDevice?: TrialDeviceDelegate })?.trialDevice;
  if (candidate && typeof candidate.findUnique === 'function' && typeof candidate.upsert === 'function') {
    return candidate;
  }
  return null;
}

export async function hasDeviceUsedTrial(
  rawDeviceId: string,
  db: PrismaClient = defaultPrisma,
): Promise<boolean> {
  const deviceId = rawDeviceId.trim();
  if (!deviceId) return false;

  await ensureTrialDevicesTable(db);
  const delegate = getDelegate(db);
  if (delegate) {
    const existing = await delegate.findUnique({
      where: { deviceId },
      select: { id: true },
    });
    return Boolean(existing);
  }
  return fallbackTrialDevices.has(deviceId);
}

export async function recordTrialDevice(
  rawDeviceId: string,
  firstUserId: string,
  db: PrismaClient = defaultPrisma,
): Promise<void> {
  const deviceId = rawDeviceId.trim();
  if (!deviceId) return;

  await ensureTrialDevicesTable(db);
  const delegate = getDelegate(db);
  if (delegate) {
    await delegate.upsert({
      where: { deviceId },
      update: {},
      create: {
        id: crypto.randomUUID(),
        deviceId,
        firstUserId,
      },
    });
    return;
  }
  fallbackTrialDevices.add(deviceId);
}
