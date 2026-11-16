import { describe, expect, it, vi } from 'vitest';
import { NetworkType, PrismaClient } from '@prisma/client';
import { publishUssdFlow, rollbackUssdFlow } from '../src/services/configService';
import { redactSensitive } from '../src/utils/logger';

describe('USSD Config Versioning & Security Redaction', () => {
  it('increments USSD flow version and disables previous enabled versions on publish', async () => {
    const updateManyMock = vi.fn().mockResolvedValue({ count: 1 });
    const createMock = vi.fn().mockImplementation(({ data }) =>
      Promise.resolve({ id: 'flow-v2', ...data }),
    );

    const mockDb = {
      $transaction: vi.fn(async (cb: (tx: unknown) => Promise<unknown>) =>
        cb({
          ussdFlowProfile: {
            findFirst: vi.fn().mockResolvedValue({ version: 1 }),
            updateMany: updateManyMock,
            create: createMock,
          },
        }),
      ),
    } as unknown as PrismaClient;

    const flow = await publishUssdFlow(
      {
        network: NetworkType.MTN,
        transactionType: 'MTN_CASH_IN',
        configuration: { entryCode: '*171#', steps: [] },
        enabled: true,
        markVerified: true,
      },
      mockDb,
    );

    expect(flow.version).toBe(2);
    expect(flow.enabled).toBe(true);
    expect(updateManyMock).toHaveBeenCalled();
  });

  it('rolls back to an earlier USSD flow version', async () => {
    const mockDb = {
      ussdFlowProfile: {
        findUnique: vi.fn().mockResolvedValue({
          id: 'flow-v1',
          network: NetworkType.MTN,
          transactionType: 'MTN_CASH_IN',
          version: 1,
        }),
      },
      $transaction: vi.fn(async (cb: (tx: unknown) => Promise<unknown>) =>
        cb({
          ussdFlowProfile: {
            updateMany: vi.fn().mockResolvedValue({ count: 1 }),
            update: vi.fn().mockResolvedValue({ id: 'flow-v1', version: 1, enabled: true }),
          },
        }),
      ),
    } as unknown as PrismaClient;

    const rolled = await rollbackUssdFlow(NetworkType.MTN, 'MTN_CASH_IN', 1, mockDb);
    expect(rolled.version).toBe(1);
    expect(rolled.enabled).toBe(true);
  });

  it('redacts PINs, passwords, tokens, and secrets in logs', () => {
    const sanitized = redactSensitive({
      pin: '1234',
      password: 'secret',
      authorization: 'Bearer abc.def.ghi',
      nested: { paystack_secret_key: 'sk_test_123' },
      safeField: 'MTN_CASH_IN',
    });

    expect(sanitized).toEqual({
      pin: '[REDACTED]',
      password: '[REDACTED]',
      authorization: '[REDACTED]',
      nested: { paystack_secret_key: '[REDACTED]' },
      safeField: 'MTN_CASH_IN',
    });
  });
});
