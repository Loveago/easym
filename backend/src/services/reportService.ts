import { NetworkType, PrismaClient, TransactionStatus, TransactionType } from '@prisma/client';
import { prisma as defaultPrisma } from '../config/prisma';

export type ReportPeriod = 'daily' | 'weekly' | 'monthly';

const PENDING_STATUSES = new Set<TransactionStatus>([
  TransactionStatus.DRAFT,
  TransactionStatus.PROCESSING,
  TransactionStatus.AWAITING_CUSTOMER,
  TransactionStatus.AWAITING_VERIFICATION,
  TransactionStatus.UNKNOWN,
]);

export function getPeriodBounds(period: ReportPeriod, referenceDate: Date = new Date()) {
  const start = new Date(
    Date.UTC(
      referenceDate.getUTCFullYear(),
      referenceDate.getUTCMonth(),
      referenceDate.getUTCDate(),
      0,
      0,
      0,
      0,
    ),
  );
  const end = new Date(start.getTime() + 24 * 60 * 60 * 1000 - 1);

  if (period === 'weekly') {
    start.setUTCDate(start.getUTCDate() - 6);
  } else if (period === 'monthly') {
    start.setUTCDate(1);
    const nextMonth = new Date(
      Date.UTC(referenceDate.getUTCFullYear(), referenceDate.getUTCMonth() + 1, 1, 0, 0, 0, 0),
    );
    return { start, end: new Date(nextMonth.getTime() - 1) };
  }

  return { start, end };
}

export async function getReportSummary(
  userId: string,
  period: ReportPeriod,
  referenceDate: Date = new Date(),
  db: PrismaClient = defaultPrisma,
) {
  const { start, end } = getPeriodBounds(period, referenceDate);

  const transactions = await db.transaction.findMany({
    where: {
      userId,
      createdAt: {
        gte: start,
        lte: end,
      },
    },
    select: {
      network: true,
      transactionType: true,
      status: true,
      amount: true,
    },
  });

  let successfulCount = 0;
  let failedCount = 0;
  let pendingCount = 0;
  let cancelledCount = 0;
  let totalSuccessfulValue = 0;

  const networkMap = new Map<
    NetworkType,
    { network: NetworkType; attempts: number; successfulCount: number; failedCount: number; successfulValue: number }
  >();
  const typeMap = new Map<
    TransactionType,
    { transactionType: TransactionType; attempts: number; successfulCount: number; failedCount: number; successfulValue: number }
  >();

  for (const tx of transactions) {
    const amountNum = Number(tx.amount);
    const isSuccess = tx.status === TransactionStatus.SUCCESSFUL;
    const isFailed = tx.status === TransactionStatus.FAILED;
    const isCancelled = tx.status === TransactionStatus.CANCELLED;
    const isPending = PENDING_STATUSES.has(tx.status);

    if (isSuccess) {
      successfulCount++;
      totalSuccessfulValue += amountNum;
    } else if (isFailed) {
      failedCount++;
    } else if (isCancelled) {
      cancelledCount++;
    } else if (isPending) {
      pendingCount++;
    }

    const netEntry = networkMap.get(tx.network) ?? {
      network: tx.network,
      attempts: 0,
      successfulCount: 0,
      failedCount: 0,
      successfulValue: 0,
    };
    netEntry.attempts++;
    if (isSuccess) {
      netEntry.successfulCount++;
      netEntry.successfulValue += amountNum;
    } else if (isFailed) {
      netEntry.failedCount++;
    }
    networkMap.set(tx.network, netEntry);

    const typeEntry = typeMap.get(tx.transactionType) ?? {
      transactionType: tx.transactionType,
      attempts: 0,
      successfulCount: 0,
      failedCount: 0,
      successfulValue: 0,
    };
    typeEntry.attempts++;
    if (isSuccess) {
      typeEntry.successfulCount++;
      typeEntry.successfulValue += amountNum;
    } else if (isFailed) {
      typeEntry.failedCount++;
    }
    typeMap.set(tx.transactionType, typeEntry);
  }

  return {
    period,
    startUtc: start.toISOString(),
    endUtc: end.toISOString(),
    totalAttempts: transactions.length,
    successfulCount,
    failedCount,
    pendingCount,
    cancelledCount,
    totalSuccessfulValue: Number(totalSuccessfulValue.toFixed(2)),
    currency: 'GHS',
    // Section 19: Do not calculate profit or commission unless actual verified commission exists
    verifiedCommissionAvailable: false,
    networkBreakdown: Array.from(networkMap.values()).map((n) => ({
      ...n,
      successfulValue: Number(n.successfulValue.toFixed(2)),
    })),
    transactionTypeBreakdown: Array.from(typeMap.values()).map((t) => ({
      ...t,
      successfulValue: Number(t.successfulValue.toFixed(2)),
    })),
  };
}
