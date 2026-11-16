import {
  NetworkType,
  Prisma,
  PrismaClient,
  ProcessingType,
  TransactionStatus,
  TransactionType,
} from '@prisma/client';
import { prisma as defaultPrisma } from '../config/prisma';
import { AppError } from '../middleware/errorHandler';
import { isValidTransactionTransition } from '../utils/stateMachine';

export interface SyncTransactionItem {
  clientTransactionId: string;
  providerReference?: string | null;
  network: NetworkType;
  transactionType: TransactionType;
  processingType: ProcessingType;
  recipientNumber: string;
  amount: number;
  bundleId?: string | null;
  bundleName?: string | null;
  status: TransactionStatus;
  confirmationSource?: string | null;
  providerResponse?: string | null;
  createdAt?: string;
  updatedAt?: string;
  events?: Array<{
    eventType: string;
    previousStatus?: TransactionStatus | null;
    newStatus: TransactionStatus;
    details?: string | null;
    createdAt?: string;
  }>;
}

export interface SyncItemResult {
  clientTransactionId: string;
  serverId?: string;
  outcome: 'created' | 'updated' | 'unchanged' | 'rejected';
  status?: TransactionStatus;
  reason?: string;
}

export async function syncTransactions(
  userId: string,
  items: SyncTransactionItem[],
  db: PrismaClient = defaultPrisma,
): Promise<{ results: SyncItemResult[] }> {
  const results: SyncItemResult[] = [];

  for (const item of items) {
    const existing = await db.transaction.findUnique({
      where: {
        userId_clientTransactionId: {
          userId,
          clientTransactionId: item.clientTransactionId,
        },
      },
    });

    if (!existing) {
      const createdAt = item.createdAt ? new Date(item.createdAt) : new Date();
      const updatedAt = item.updatedAt ? new Date(item.updatedAt) : createdAt;

      const created = await db.$transaction(async (tx) => {
        const record = await tx.transaction.create({
          data: {
            userId,
            clientTransactionId: item.clientTransactionId,
            providerReference: item.providerReference ?? null,
            network: item.network,
            transactionType: item.transactionType,
            processingType: item.processingType,
            recipientNumber: item.recipientNumber,
            amount: new Prisma.Decimal(item.amount.toFixed(2)),
            bundleId: item.bundleId ?? null,
            bundleName: item.bundleName ?? null,
            status: item.status,
            confirmationSource: item.confirmationSource ?? null,
            providerResponse: item.providerResponse ?? null,
            createdAt,
            updatedAt,
          },
        });

        const eventsToCreate =
          item.events && item.events.length > 0
            ? item.events.map((ev) => ({
                transactionId: record.id,
                eventType: ev.eventType,
                previousStatus: ev.previousStatus ?? null,
                newStatus: ev.newStatus,
                details: ev.details ?? null,
                createdAt: ev.createdAt ? new Date(ev.createdAt) : createdAt,
              }))
            : [
                {
                  transactionId: record.id,
                  eventType: 'SYNC_CREATED',
                  previousStatus: null,
                  newStatus: item.status,
                  details: 'Initial offline sync',
                  createdAt,
                },
              ];

        await tx.transactionEvent.createMany({ data: eventsToCreate });
        return record;
      });

      results.push({
        clientTransactionId: item.clientTransactionId,
        serverId: created.id,
        outcome: 'created',
        status: created.status,
      });
      continue;
    }

    // Existing transaction: check idempotency and state transition rules
    if (existing.status === item.status) {
      results.push({
        clientTransactionId: item.clientTransactionId,
        serverId: existing.id,
        outcome: 'unchanged',
        status: existing.status,
      });
      continue;
    }

    if (!isValidTransactionTransition(existing.status, item.status)) {
      results.push({
        clientTransactionId: item.clientTransactionId,
        serverId: existing.id,
        outcome: 'rejected',
        status: existing.status,
        reason: `Invalid status transition from ${existing.status} to ${item.status}`,
      });
      continue;
    }

    const updated = await db.$transaction(async (tx) => {
      const record = await tx.transaction.update({
        where: { id: existing.id },
        data: {
          status: item.status,
          providerReference: item.providerReference ?? existing.providerReference,
          confirmationSource: item.confirmationSource ?? existing.confirmationSource,
          providerResponse: item.providerResponse ?? existing.providerResponse,
        },
      });

      await tx.transactionEvent.create({
        data: {
          transactionId: record.id,
          eventType: 'STATUS_SYNCED',
          previousStatus: existing.status,
          newStatus: item.status,
          details: item.confirmationSource
            ? `Synced with confirmation source: ${item.confirmationSource}`
            : 'Status updated via sync',
        },
      });

      return record;
    });

    results.push({
      clientTransactionId: item.clientTransactionId,
      serverId: updated.id,
      outcome: 'updated',
      status: updated.status,
    });
  }

  return { results };
}

export interface TransactionFilterQuery {
  search?: string;
  network?: NetworkType;
  transactionType?: TransactionType;
  status?: TransactionStatus;
  fromDate?: string;
  toDate?: string;
  page?: number;
  limit?: number;
}

export async function listTransactions(
  userId: string,
  filters: TransactionFilterQuery,
  db: PrismaClient = defaultPrisma,
) {
  const page = filters.page ?? 1;
  const limit = filters.limit ?? 25;
  const skip = (page - 1) * limit;

  const where: Prisma.TransactionWhereInput = {
    userId,
    ...(filters.network ? { network: filters.network } : {}),
    ...(filters.transactionType ? { transactionType: filters.transactionType } : {}),
    ...(filters.status ? { status: filters.status } : {}),
    ...(filters.fromDate || filters.toDate
      ? {
          createdAt: {
            ...(filters.fromDate ? { gte: new Date(filters.fromDate) } : {}),
            ...(filters.toDate ? { lte: new Date(filters.toDate) } : {}),
          },
        }
      : {}),
    ...(filters.search
      ? {
          OR: [
            { recipientNumber: { contains: filters.search, mode: 'insensitive' } },
            { providerReference: { contains: filters.search, mode: 'insensitive' } },
            { clientTransactionId: { contains: filters.search, mode: 'insensitive' } },
            { bundleName: { contains: filters.search, mode: 'insensitive' } },
          ],
        }
      : {}),
  };

  const [total, rows] = await Promise.all([
    db.transaction.count({ where }),
    db.transaction.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      skip,
      take: limit,
    }),
  ]);

  return {
    pagination: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit),
    },
    transactions: rows.map((t) => ({
      id: t.id,
      clientTransactionId: t.clientTransactionId,
      providerReference: t.providerReference,
      network: t.network,
      transactionType: t.transactionType,
      processingType: t.processingType,
      recipientNumber: t.recipientNumber,
      amount: Number(t.amount),
      bundleId: t.bundleId,
      bundleName: t.bundleName,
      status: t.status,
      confirmationSource: t.confirmationSource,
      providerResponse: t.providerResponse,
      createdAt: t.createdAt.toISOString(),
      updatedAt: t.updatedAt.toISOString(),
    })),
  };
}

export async function getTransactionById(
  userId: string,
  id: string,
  db: PrismaClient = defaultPrisma,
) {
  const tx = await db.transaction.findFirst({
    where: {
      userId,
      OR: [{ id }, { clientTransactionId: id }],
    },
    include: {
      events: {
        orderBy: { createdAt: 'asc' },
      },
    },
  });

  if (!tx) {
    throw new AppError(404, 'TRANSACTION_NOT_FOUND', 'Transaction not found');
  }

  return {
    id: tx.id,
    clientTransactionId: tx.clientTransactionId,
    providerReference: tx.providerReference,
    network: tx.network,
    transactionType: tx.transactionType,
    processingType: tx.processingType,
    recipientNumber: tx.recipientNumber,
    amount: Number(tx.amount),
    bundleId: tx.bundleId,
    bundleName: tx.bundleName,
    status: tx.status,
    confirmationSource: tx.confirmationSource,
    providerResponse: tx.providerResponse,
    createdAt: tx.createdAt.toISOString(),
    updatedAt: tx.updatedAt.toISOString(),
    events: tx.events.map((e) => ({
      id: e.id,
      eventType: e.eventType,
      previousStatus: e.previousStatus,
      newStatus: e.newStatus,
      details: e.details,
      createdAt: e.createdAt.toISOString(),
    })),
  };
}
