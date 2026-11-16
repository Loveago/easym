import crypto from 'crypto';
import { describe, expect, it, vi } from 'vitest';
import { EntitlementStatus, PaymentStatus, Prisma, PrismaClient } from '@prisma/client';
import { env } from '../src/config/env';
import { PaystackGateway, verifyPaystackSignature } from '../src/services/paystackService';
import {
  handlePaystackWebhook,
  verifyAndActivateSubscription,
} from '../src/services/subscriptionService';

describe('Paystack Subscriptions & Webhook Security', () => {
  it('verifies valid HMAC-SHA512 webhook signature and rejects invalid ones', () => {
    const secret = 'sk_test_super_secret_key';
    const rawBody = Buffer.from(JSON.stringify({ event: 'charge.success', data: { reference: 'AM_123' } }));
    const validSig = crypto.createHmac('sha512', secret).update(rawBody).digest('hex');

    expect(verifyPaystackSignature(rawBody, validSig, secret)).toBe(true);
    expect(verifyPaystackSignature(rawBody, 'bad-signature', secret)).toBe(false);
    expect(verifyPaystackSignature(rawBody, undefined, secret)).toBe(false);
  });

  it('rejects webhook with invalid signature', async () => {
    const rawBody = Buffer.from(JSON.stringify({ event: 'charge.success' }));
    await expect(
      handlePaystackWebhook(rawBody, 'wrong-sig', { event: 'charge.success' }),
    ).rejects.toMatchObject({ statusCode: 401, code: 'INVALID_WEBHOOK_SIGNATURE' });
  });

  it('does not activate subscription when Paystack verification reports failed payment', async () => {
    const updateMock = vi.fn().mockResolvedValue({});
    const mockDb = {
      payment: {
        findUnique: vi.fn().mockResolvedValue({
          id: 'pay-1',
          userId: 'user-1',
          planId: 'plan-1',
          reference: 'AM_FAIL_1',
          amount: new Prisma.Decimal('50.00'),
          currency: 'GHS',
          status: PaymentStatus.PENDING,
          plan: { id: 'plan-1', durationDays: 30 },
        }),
        update: updateMock,
      },
    } as unknown as PrismaClient;

    const mockGateway: PaystackGateway = {
      initializeTransaction: vi.fn(),
      verifyTransaction: vi.fn().mockResolvedValue({
        reference: 'AM_FAIL_1',
        status: 'failed',
        amount: 5000,
        currency: 'GHS',
        channel: 'mobile_money',
        paidAt: null,
        authorization: null,
        raw: {},
      }),
    };

    await expect(
      verifyAndActivateSubscription('AM_FAIL_1', 'user-1', mockDb, mockGateway),
    ).rejects.toMatchObject({ statusCode: 400, code: 'PAYMENT_NOT_SUCCESSFUL' });

    expect(updateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ status: PaymentStatus.FAILED }),
      }),
    );
  });

  it('activates subscription on verified payment and handles MoMo non-reusable auth honestly', async () => {
    const upsertEntitlementMock = vi.fn().mockResolvedValue({
      id: 'ent-1',
      userId: 'user-1',
      status: EntitlementStatus.ACTIVE,
      trialStartedAt: null,
      trialExpiresAt: null,
      paidUntil: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      currentPlanId: 'plan-1',
      recurringAuthorized: false,
    });

    const mockDb = {
      payment: {
        findUnique: vi.fn().mockResolvedValue({
          id: 'pay-2',
          userId: 'user-1',
          planId: 'plan-1',
          reference: 'AM_OK_1',
          amount: new Prisma.Decimal('50.00'),
          currency: 'GHS',
          status: PaymentStatus.PENDING,
          plan: { id: 'plan-1', durationDays: 30 },
        }),
      },
      $transaction: vi.fn(async (cb: (tx: unknown) => Promise<unknown>) => {
        const tx = {
          payment: {
            findUnique: vi.fn().mockResolvedValue({ id: 'pay-2', status: PaymentStatus.PENDING }),
            update: vi.fn().mockResolvedValue({
              id: 'pay-2',
              reference: 'AM_OK_1',
              status: PaymentStatus.SUCCESS,
              amount: new Prisma.Decimal('50.00'),
              currency: 'GHS',
              channel: 'mobile_money',
              paidAt: new Date(),
            }),
          },
          subscriptionEntitlement: {
            findUnique: vi.fn().mockResolvedValue(null),
            upsert: upsertEntitlementMock,
          },
        };
        return cb(tx);
      }),
    } as unknown as PrismaClient;

    const mockGateway: PaystackGateway = {
      initializeTransaction: vi.fn(),
      verifyTransaction: vi.fn().mockResolvedValue({
        reference: 'AM_OK_1',
        status: 'success',
        amount: 5000,
        currency: 'GHS',
        channel: 'mobile_money',
        paidAt: new Date().toISOString(),
        authorization: { reusable: false, authorizationCode: null },
        raw: { status: 'success' },
      }),
    };

    const res = await verifyAndActivateSubscription('AM_OK_1', 'user-1', mockDb, mockGateway);

    expect(res.alreadyProcessed).toBe(false);
    expect(res.entitlement?.status).toBe(EntitlementStatus.ACTIVE);
    expect(res.entitlement?.recurringAuthorized).toBe(false);
    expect(upsertEntitlementMock).toHaveBeenCalledWith(
      expect.objectContaining({
        update: expect.objectContaining({
          status: EntitlementStatus.ACTIVE,
          recurringAuthorized: false,
          authorizationCode: null,
        }),
      }),
    );
  });

  it('is idempotent on duplicate webhook delivery (does not double-extend subscription)', async () => {
    const existingPaidUntil = new Date('2026-12-15T00:00:00.000Z');
    const mockDb = {
      payment: {
        findUnique: vi.fn().mockResolvedValue({
          id: 'pay-3',
          userId: 'user-1',
          planId: 'plan-1',
          reference: 'AM_DUP_1',
          amount: new Prisma.Decimal('50.00'),
          currency: 'GHS',
          channel: 'mobile_money',
          status: PaymentStatus.SUCCESS,
          paidAt: new Date('2026-11-15T00:00:00.000Z'),
          plan: { id: 'plan-1', durationDays: 30 },
        }),
      },
      subscriptionEntitlement: {
        findUnique: vi.fn().mockResolvedValue({
          id: 'ent-1',
          userId: 'user-1',
          status: EntitlementStatus.ACTIVE,
          trialStartedAt: null,
          trialExpiresAt: null,
          paidUntil: existingPaidUntil,
          currentPlanId: 'plan-1',
          recurringAuthorized: false,
        }),
      },
    } as unknown as PrismaClient;

    const payload = { event: 'charge.success', data: { reference: 'AM_DUP_1' } };
    const rawBody = Buffer.from(JSON.stringify(payload));
    const sig = crypto
      .createHmac('sha512', env.PAYSTACK_SECRET_KEY)
      .update(rawBody)
      .digest('hex');

    const res = await handlePaystackWebhook(rawBody, sig, payload, mockDb);
    expect(res.received).toBe(true);
    expect(res.alreadyProcessed).toBe(true);
  });
});
