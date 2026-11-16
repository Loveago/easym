import { describe, expect, it, vi } from 'vitest';
import {
  NetworkType,
  Prisma,
  PrismaClient,
  TransactionStatus,
  TransactionType,
} from '@prisma/client';
import { getReportSummary } from '../src/services/reportService';

describe('Reports & Analytics Service', () => {
  it('aggregates daily/weekly/monthly metrics accurately without inventing commission', async () => {
    const mockDb = {
      transaction: {
        findMany: vi.fn().mockResolvedValue([
          {
            network: NetworkType.MTN,
            transactionType: TransactionType.CASH_IN,
            status: TransactionStatus.SUCCESSFUL,
            amount: new Prisma.Decimal('100.00'),
          },
          {
            network: NetworkType.MTN,
            transactionType: TransactionType.CASH_OUT,
            status: TransactionStatus.FAILED,
            amount: new Prisma.Decimal('50.00'),
          },
          {
            network: NetworkType.AT,
            transactionType: TransactionType.AIRTIME,
            status: TransactionStatus.AWAITING_CUSTOMER,
            amount: new Prisma.Decimal('20.00'),
          },
        ]),
      },
    } as unknown as PrismaClient;

    const summary = await getReportSummary(
      'user-1',
      'daily',
      new Date('2026-11-15T12:00:00.000Z'),
      mockDb,
    );

    expect(summary.period).toBe('daily');
    expect(summary.totalAttempts).toBe(3);
    expect(summary.successfulCount).toBe(1);
    expect(summary.failedCount).toBe(1);
    expect(summary.pendingCount).toBe(1);
    expect(summary.totalSuccessfulValue).toBe(100);
    expect(summary.verifiedCommissionAvailable).toBe(false);
    expect(summary.networkBreakdown).toHaveLength(2);
    expect(summary.transactionTypeBreakdown).toHaveLength(3);
  });
});
