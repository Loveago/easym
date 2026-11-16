import { CustomerNumber, PrismaClient } from '@prisma/client';
import { prisma as defaultPrisma } from '../config/prisma';
import { AppError } from '../middleware/errorHandler';
import { decryptText, encryptText, hashNormalizedPhone } from '../utils/crypto';

export interface CustomerSyncInput {
  cloudId?: string | null;
  phoneNumber: string;
  normalizedPhoneNumber?: string | null;
  nickname?: string | null;
  firstUsedAt: number; // epoch ms
  lastUsedAt: number; // epoch ms
  usageCount: number;
  updatedAt: number; // epoch ms
  deletedAt?: number | null; // epoch ms
}

export interface CustomerSyncDto {
  id: string;
  normalizedPhoneNumber: string;
  phoneNumber: string;
  nickname: string | null;
  firstUsedAt: number;
  lastUsedAt: number;
  usageCount: number;
  version: number;
  deletedAt: number | null;
  syncedAt: number;
  updatedAt: number;
}

export function normalizeGhanaPhone(raw: string): string {
  const digits = raw.replace(/\D/g, '');
  if (digits.startsWith('233') && digits.length === 12) {
    return `0${digits.slice(3)}`;
  }
  if (digits.startsWith('0') && digits.length === 10) {
    return digits;
  }
  if (digits.length === 9 && !digits.startsWith('0')) {
    return `0${digits}`;
  }
  return digits;
}

export function isValidGhanaPhone(raw: string): boolean {
  const norm = normalizeGhanaPhone(raw);
  return norm.length === 10 && norm.startsWith('0') && (norm[1] === '2' || norm[1] === '5');
}

function toDto(row: CustomerNumber): CustomerSyncDto {
  return {
    id: row.id,
    normalizedPhoneNumber: decryptText(row.encryptedNormalizedPhone),
    phoneNumber: decryptText(row.encryptedDisplayPhone),
    nickname: row.encryptedNickname ? decryptText(row.encryptedNickname) : null,
    firstUsedAt: row.firstUsedAt.getTime(),
    lastUsedAt: row.lastUsedAt.getTime(),
    usageCount: row.usageCount,
    version: row.version,
    deletedAt: row.deletedAt ? row.deletedAt.getTime() : null,
    syncedAt: row.syncedAt.getTime(),
    updatedAt: row.updatedAt.getTime(),
  };
}

export async function syncCustomerBatch(
  userId: string,
  items: CustomerSyncInput[],
  db: PrismaClient = defaultPrisma,
): Promise<{ synced: CustomerSyncDto[]; serverTime: number }> {
  const results: CustomerSyncDto[] = [];
  const now = new Date();

  for (const item of items) {
    const rawCandidate = (item.normalizedPhoneNumber || item.phoneNumber || '').trim();
    const normalized = normalizeGhanaPhone(rawCandidate);
    if (!isValidGhanaPhone(normalized)) {
      throw new AppError(400, 'INVALID_PHONE', 'One or more customer phone numbers are invalid.');
    }

    const displayPhone = item.phoneNumber.trim() || normalized;
    const phoneHash = hashNormalizedPhone(userId, normalized);
    const incomingFirst = new Date(Math.min(item.firstUsedAt, item.lastUsedAt));
    const incomingLast = new Date(Math.max(item.firstUsedAt, item.lastUsedAt));
    const incomingUpdated = new Date(item.updatedAt || item.lastUsedAt);
    const incomingDeleted = item.deletedAt ? new Date(item.deletedAt) : null;
    const incomingUsage = Math.max(1, Math.floor(item.usageCount || 1));

    const existing = await db.customerNumber.findUnique({
      where: {
        userId_phoneHash: { userId, phoneHash },
      },
    });

    if (!existing) {
      const created = await db.customerNumber.create({
        data: {
          userId,
          phoneHash,
          encryptedNormalizedPhone: encryptText(normalized),
          encryptedDisplayPhone: encryptText(displayPhone),
          encryptedNickname: item.nickname?.trim() ? encryptText(item.nickname.trim()) : null,
          firstUsedAt: incomingFirst,
          lastUsedAt: incomingLast,
          usageCount: incomingUsage,
          version: 1,
          deletedAt: incomingDeleted,
          syncedAt: now,
        },
      });
      results.push(toDto(created));
      continue;
    }

    // Conflict resolution with deletion tombstones
    const mergedFirst =
      incomingFirst < existing.firstUsedAt ? incomingFirst : existing.firstUsedAt;
    const mergedLast = incomingLast > existing.lastUsedAt ? incomingLast : existing.lastUsedAt;
    const mergedUsage = Math.max(existing.usageCount, incomingUsage);

    let mergedDeletedAt: Date | null = existing.deletedAt;
    if (existing.deletedAt && !incomingDeleted) {
      // Only resurrect if the agent actively used the number AFTER it was deleted
      if (incomingLast.getTime() > existing.deletedAt.getTime()) {
        mergedDeletedAt = null;
      } else {
        mergedDeletedAt = existing.deletedAt;
      }
    } else if (incomingDeleted) {
      if (!existing.deletedAt || incomingDeleted > existing.deletedAt) {
        // Keep deletion unless the existing record was actively used after incomingDeleted
        mergedDeletedAt =
          existing.lastUsedAt.getTime() > incomingDeleted.getTime()
            ? existing.deletedAt
            : incomingDeleted;
      }
    }

    const incomingIsNewer = incomingUpdated.getTime() >= existing.updatedAt.getTime();
    const mergedDisplay = incomingIsNewer
      ? displayPhone
      : decryptText(existing.encryptedDisplayPhone);
    const mergedNickname = incomingIsNewer
      ? item.nickname?.trim() || null
      : existing.encryptedNickname
        ? decryptText(existing.encryptedNickname)
        : null;

    const updated = await db.customerNumber.update({
      where: { id: existing.id },
      data: {
        encryptedNormalizedPhone: encryptText(normalized),
        encryptedDisplayPhone: encryptText(mergedDisplay),
        encryptedNickname: mergedNickname ? encryptText(mergedNickname) : null,
        firstUsedAt: mergedFirst,
        lastUsedAt: mergedLast,
        usageCount: mergedUsage,
        version: existing.version + 1,
        deletedAt: mergedDeletedAt,
        syncedAt: now,
      },
    });
    results.push(toDto(updated));
  }

  return { synced: results, serverTime: now.getTime() };
}

export async function listCustomersForAgent(
  userId: string,
  options: { sinceMs?: number; cursor?: string; limit?: number; includeDeleted?: boolean },
  db: PrismaClient = defaultPrisma,
): Promise<{
  customers: CustomerSyncDto[];
  nextCursor: string | null;
  hasMore: boolean;
  serverTime: number;
}> {
  const limit = Math.min(Math.max(options.limit ?? 100, 1), 500);
  const where: Record<string, unknown> = { userId };

  if (options.sinceMs && options.sinceMs > 0) {
    where.updatedAt = { gt: new Date(options.sinceMs) };
  } else if (!options.includeDeleted) {
    where.deletedAt = null;
  }

  const rows = await db.customerNumber.findMany({
    where,
    orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }],
    take: limit + 1,
    ...(options.cursor ? { cursor: { id: options.cursor }, skip: 1 } : {}),
  });

  const hasMore = rows.length > limit;
  const page = hasMore ? rows.slice(0, limit) : rows;
  const nextCursor = hasMore && page.length > 0 ? page[page.length - 1].id : null;

  return {
    customers: page.map(toDto),
    nextCursor,
    hasMore,
    serverTime: Date.now(),
  };
}

export async function deleteCustomerForAgent(
  userId: string,
  recordId: string,
  db: PrismaClient = defaultPrisma,
): Promise<CustomerSyncDto> {
  const existing = await db.customerNumber.findFirst({
    where: { id: recordId, userId },
  });
  if (!existing) {
    throw new AppError(404, 'CUSTOMER_NOT_FOUND', 'Customer record not found');
  }
  const now = new Date();
  const updated = await db.customerNumber.update({
    where: { id: existing.id },
    data: {
      deletedAt: now,
      version: existing.version + 1,
      syncedAt: now,
    },
  });
  return toDto(updated);
}

export async function clearCustomersForAgent(
  userId: string,
  db: PrismaClient = defaultPrisma,
): Promise<{ deletedCount: number; serverTime: number }> {
  const now = new Date();
  const res = await db.customerNumber.updateMany({
    where: { userId, deletedAt: null },
    data: {
      deletedAt: now,
      syncedAt: now,
    },
  });
  return { deletedCount: res.count, serverTime: now.getTime() };
}

export async function getCustomerSyncStatus(
  userId: string,
  db: PrismaClient = defaultPrisma,
) {
  const [activeCount, deletedCount, latest] = await Promise.all([
    db.customerNumber.count({ where: { userId, deletedAt: null } }),
    db.customerNumber.count({ where: { userId, deletedAt: { not: null } } }),
    db.customerNumber.findFirst({
      where: { userId },
      orderBy: { updatedAt: 'desc' },
      select: { updatedAt: true },
    }),
  ]);
  return {
    activeCount,
    deletedCount,
    lastUpdatedAt: latest ? latest.updatedAt.getTime() : null,
    serverTime: Date.now(),
  };
}
