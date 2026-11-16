import { describe, expect, it, vi } from 'vitest';
import {
  NetworkType,
  PrismaClient,
  ProcessingType,
  TransactionStatus,
  TransactionType,
} from '@prisma/client';
import { syncTransactions } from '../src/services/transactionService';
import { isValidTransactionTransition } from '../src/utils/stateMachine';

describe('Transaction Lifecycle & Idempotent Offline Sync', () => {
  it('enforces controlled state transitions and blocks UNKNOWN -> SUCCESSFUL', () => {
    expect(
      isValidTransactionTransition(TransactionStatus.DRAFT, TransactionStatus.PROCESSING),
    ).toBe(true);
    expect(
      isValidTransactionTransition(TransactionStatus.PROCESSING, TransactionStatus.SUCCESSFUL),
    ).toBe(true);
    expect(
      isValidTransactionTransition(
        TransactionStatus.AWAITING_CUSTOMER,
        TransactionStatus.AWAITING_VERIFICATION,
      ),
    ).toBe(true);
    expect(
      isValidTransactionTransition(TransactionStatus.UNKNOWN, TransactionStatus.SUCCESSFUL),
    ).toBe(false);
    expect(
      isValidTransactionTransition(TransactionStatus.SUCCESSFUL, TransactionStatus.FAILED),
    ).toBe(false);
  });

  it('creates new transaction on first sync and is idempotent on retry', async () => {
    const createMock = vi.fn().mockResolvedValue({
      id: 'srv-tx-1',
      status: TransactionStatus.SUCCESSFUL,
    });
    const createEventsMock = vi.fn().mockResolvedValue({ count: 1 });

    const mockDbFirst = {
      transaction: {
        findUnique: vi.fn().mockResolvedValue(null),
      },
      $transaction: vi.fn(async (cb: (tx: unknown) => Promise<unknown>) =>
        cb({
          transaction: { create: createMock },
          transactionEvent: { createMany: createEventsMock },
        }),
      ),
    } as unknown as PrismaClient;

    const item = {
      clientTransactionId: 'local-uuid-1',
      network: NetworkType.MTN,
      transactionType: TransactionType.CASH_IN,
      processingType: ProcessingType.AUTOMATED_USSD,
      recipientNumber: '0241234567',
      amount: 150,
      status: TransactionStatus.SUCCESSFUL,
      confirmationSource: 'USSD_RESPONSE',
    };

    const first = await syncTransactions('user-1', [item], mockDbFirst);
    expect(first.results[0]).toMatchObject({
      clientTransactionId: 'local-uuid-1',
      serverId: 'srv-tx-1',
      outcome: 'created',
      status: TransactionStatus.SUCCESSFUL,
    });

    // Retry same payload
    const mockDbRetry = {
      transaction: {
        findUnique: vi.fn().mockResolvedValue({
          id: 'srv-tx-1',
          status: TransactionStatus.SUCCESSFUL,
        }),
      },
    } as unknown as PrismaClient;

    const retry = await syncTransactions('user-1', [item], mockDbRetry);
    expect(retry.results[0]).toMatchObject({
      clientTransactionId: 'local-uuid-1',
      serverId: 'srv-tx-1',
      outcome: 'unchanged',
      status: TransactionStatus.SUCCESSFUL,
    });
  });

  it('rejects invalid state transition during sync without mutating record', async () => {
    const mockDb = {
      transaction: {
        findUnique: vi.fn().mockResolvedValue({
          id: 'srv-tx-2',
          status: TransactionStatus.UNKNOWN,
        }),
      },
    } as unknown as PrismaClient;

    const res = await syncTransactions(
      'user-1',
      [
        {
          clientTransactionId: 'local-uuid-2',
          network: NetworkType.TELECEL,
          transactionType: TransactionType.CASH_IN,
          processingType: ProcessingType.AUTOMATED_USSD,
          recipientNumber: '0201234567',
          amount: 200,
          status: TransactionStatus.SUCCESSFUL,
        },
      ],
      mockDb,
    );

    expect(res.results[0]).toMatchObject({
      clientTransactionId: 'local-uuid-2',
      outcome: 'rejected',
      status: TransactionStatus.UNKNOWN,
    });
  });
});
