import { PrismaClient } from '@prisma/client';
import { env } from '../config/env';
import { prisma as defaultPrisma } from '../config/prisma';
import { AppError } from '../middleware/errorHandler';

export const MAX_ACTIVE_DEVICES_PER_ACCOUNT = 2;
const TOUCH_THROTTLE_MS = 60_000;

interface DeviceRecord {
  deviceId: string;
  deviceName: string | null;
  lastActiveAt: Date;
}

// Fallback store for unit tests where PrismaClient mock does not define userDevice,
// plus 60s verification cache to avoid redundant DB queries on rapid API calls.
const fallbackDevicesByUser = new Map<string, Map<string, DeviceRecord>>();
const verifiedDeviceTouchCache = new Map<string, number>();
let tableEnsured = false;

export function clearDeviceLimitTestState(): void {
  fallbackDevicesByUser.clear();
  verifiedDeviceTouchCache.clear();
}

async function ensureUserDevicesTable(db: PrismaClient): Promise<void> {
  if (tableEnsured || env.NODE_ENV === 'test') return;
  const rawExec = (db as unknown as { $executeRawUnsafe?: (sql: string) => Promise<unknown> })
    .$executeRawUnsafe;
  if (typeof rawExec !== 'function') return;
  try {
    await rawExec.call(
      db,
      `CREATE TABLE IF NOT EXISTS "user_devices" (
        "id" TEXT NOT NULL,
        "user_id" TEXT NOT NULL,
        "device_id" TEXT NOT NULL,
        "device_name" TEXT,
        "last_active_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "user_devices_pkey" PRIMARY KEY ("id")
      )`,
    );
    await rawExec.call(
      db,
      `CREATE UNIQUE INDEX IF NOT EXISTS "user_devices_user_id_device_id_key" ON "user_devices"("user_id", "device_id")`,
    );
    await rawExec.call(
      db,
      `CREATE INDEX IF NOT EXISTS "user_devices_user_id_idx" ON "user_devices"("user_id")`,
    );
    tableEnsured = true;
  } catch {
    // Managed by Prisma migrations
  }
}

type UserDeviceDelegate = {
  findMany: (args: unknown) => Promise<Array<{ id: string; deviceId: string; deviceName: string | null; lastActiveAt: Date }>>;
  create: (args: unknown) => Promise<unknown>;
  update: (args: unknown) => Promise<unknown>;
  deleteMany: (args: unknown) => Promise<{ count: number }>;
};

function getDelegate(db: PrismaClient): UserDeviceDelegate | null {
  if (db === defaultPrisma && env.NODE_ENV === 'test') {
    return null;
  }
  const candidate = (db as unknown as { userDevice?: UserDeviceDelegate })?.userDevice;
  if (candidate && typeof candidate.findMany === 'function') {
    return candidate;
  }
  return null;
}

export async function verifyAndRegisterDevice(
  userId: string,
  rawDeviceId?: string | null,
  rawDeviceName?: string | null,
  db: PrismaClient = defaultPrisma,
  options: { bypassThrottle?: boolean } = {},
): Promise<{ activeDevices: number; maxDevices: number }> {
  const deviceId = (rawDeviceId ?? '').trim();
  if (!deviceId) {
    return { activeDevices: 0, maxDevices: MAX_ACTIVE_DEVICES_PER_ACCOUNT };
  }
  const deviceName = (rawDeviceName ?? '').trim().slice(0, 120) || null;
  const cacheKey = `${userId}::${deviceId}`;
  const nowMs = Date.now();

  if (!options.bypassThrottle) {
    const lastVerified = verifiedDeviceTouchCache.get(cacheKey);
    if (lastVerified && nowMs - lastVerified < TOUCH_THROTTLE_MS) {
      return { activeDevices: 1, maxDevices: MAX_ACTIVE_DEVICES_PER_ACCOUNT };
    }
  }

  await ensureUserDevicesTable(db);
  const delegate = getDelegate(db);
  const now = new Date(nowMs);

  if (delegate) {
    const existingDevices = await delegate.findMany({
      where: { userId },
      orderBy: { createdAt: 'asc' },
    });
    const matched = existingDevices.find((d) => d.deviceId === deviceId);
    if (matched) {
      await delegate.update({
        where: { id: matched.id },
        data: {
          lastActiveAt: now,
          ...(deviceName ? { deviceName } : {}),
        },
      });
      verifiedDeviceTouchCache.set(cacheKey, nowMs);
      return {
        activeDevices: existingDevices.length,
        maxDevices: MAX_ACTIVE_DEVICES_PER_ACCOUNT,
      };
    }

    if (existingDevices.length >= MAX_ACTIVE_DEVICES_PER_ACCOUNT) {
      verifiedDeviceTouchCache.delete(cacheKey);
      throw new AppError(
        403,
        'DEVICE_LIMIT_REACHED',
        `Maximum of ${MAX_ACTIVE_DEVICES_PER_ACCOUNT} active devices reached for this account. Please sign out on one of your active devices first.`,
      );
    }

    await delegate.create({
      data: {
        userId,
        deviceId,
        deviceName,
        lastActiveAt: now,
      },
    });
    verifiedDeviceTouchCache.set(cacheKey, nowMs);
    return {
      activeDevices: existingDevices.length + 1,
      maxDevices: MAX_ACTIVE_DEVICES_PER_ACCOUNT,
    };
  }

  // Fallback in-memory enforcement (used in unit/HTTP tests without live DB delegate)
  let userMap = fallbackDevicesByUser.get(userId);
  if (!userMap) {
    userMap = new Map<string, DeviceRecord>();
    fallbackDevicesByUser.set(userId, userMap);
  }

  const existing = userMap.get(deviceId);
  if (existing) {
    userMap.set(deviceId, {
      deviceId,
      deviceName: deviceName ?? existing.deviceName,
      lastActiveAt: now,
    });
    verifiedDeviceTouchCache.set(cacheKey, nowMs);
    return { activeDevices: userMap.size, maxDevices: MAX_ACTIVE_DEVICES_PER_ACCOUNT };
  }

  if (userMap.size >= MAX_ACTIVE_DEVICES_PER_ACCOUNT) {
    verifiedDeviceTouchCache.delete(cacheKey);
    throw new AppError(
      403,
      'DEVICE_LIMIT_REACHED',
      `Maximum of ${MAX_ACTIVE_DEVICES_PER_ACCOUNT} active devices reached for this account. Please sign out on one of your active devices first.`,
    );
  }

  userMap.set(deviceId, {
    deviceId,
    deviceName,
    lastActiveAt: now,
  });
  verifiedDeviceTouchCache.set(cacheKey, nowMs);
  return { activeDevices: userMap.size, maxDevices: MAX_ACTIVE_DEVICES_PER_ACCOUNT };
}

export async function unregisterDevice(
  userId: string,
  rawDeviceId?: string | null,
  db: PrismaClient = defaultPrisma,
): Promise<void> {
  const deviceId = (rawDeviceId ?? '').trim();
  if (!deviceId) return;

  verifiedDeviceTouchCache.delete(`${userId}::${deviceId}`);
  await ensureUserDevicesTable(db);
  const delegate = getDelegate(db);
  if (delegate) {
    await delegate.deleteMany({
      where: { userId, deviceId },
    });
    return;
  }

  const userMap = fallbackDevicesByUser.get(userId);
  userMap?.delete(deviceId);
}
