import { describe, expect, it, vi } from 'vitest';
import { EntitlementStatus, PrismaClient } from '@prisma/client';
import {
  TRIAL_DURATION_DAYS,
  computeEntitlementSummary,
  getAndSyncEntitlement,
} from '../src/services/trialService';

describe('Fifteen-Day Free Trial & Entitlement Service', () => {
  const baseDate = new Date('2026-11-01T10:00:00.000Z');
  const trialEnd = new Date(
    baseDate.getTime() + TRIAL_DURATION_DAYS * 24 * 60 * 60 * 1000,
  );

  it('grants access with 15 days remaining when trial just started', () => {
    const summary = computeEntitlementSummary(
      {
        status: EntitlementStatus.TRIAL,
        trialStartedAt: baseDate,
        trialExpiresAt: trialEnd,
        paidUntil: null,
        currentPlanId: null,
        recurringAuthorized: false,
      },
      baseDate,
    );

    expect(summary.status).toBe(EntitlementStatus.TRIAL);
    expect(summary.hasAccess).toBe(true);
    expect(summary.isTrial).toBe(true);
    expect(summary.remainingDays).toBe(15);
    expect(summary.displayMessage).toBe('15 days remaining');
  });

  it('exposes the current plan name when present', () => {
    const summary = computeEntitlementSummary(
      {
        status: EntitlementStatus.ACTIVE,
        trialStartedAt: null,
        trialExpiresAt: null,
        paidUntil: new Date('2026-12-01T10:00:00.000Z'),
        currentPlanId: 'plan-1',
        recurringAuthorized: false,
        currentPlan: { name: 'Monthly Agent Plan' },
      },
      baseDate,
    );
    expect(summary.currentPlanName).toBe('Monthly Agent Plan');
  });

  it('shows "Trial ends today" when less than 24 hours remain', () => {
    const almostExpired = new Date(trialEnd.getTime() - 4 * 60 * 60 * 1000);
    const summary = computeEntitlementSummary(
      {
        status: EntitlementStatus.TRIAL,
        trialStartedAt: baseDate,
        trialExpiresAt: trialEnd,
        paidUntil: null,
        currentPlanId: null,
        recurringAuthorized: false,
      },
      almostExpired,
    );

    expect(summary.status).toBe(EntitlementStatus.TRIAL);
    expect(summary.hasAccess).toBe(true);
    expect(summary.remainingDays).toBe(1);
    expect(summary.displayMessage).toBe('Trial ends today');
  });

  it('expires trial after 15 days using server UTC time', () => {
    const afterExpiry = new Date(trialEnd.getTime() + 1000);
    const summary = computeEntitlementSummary(
      {
        status: EntitlementStatus.TRIAL,
        trialStartedAt: baseDate,
        trialExpiresAt: trialEnd,
        paidUntil: null,
        currentPlanId: null,
        recurringAuthorized: false,
      },
      afterExpiry,
    );

    expect(summary.status).toBe(EntitlementStatus.EXPIRED);
    expect(summary.hasAccess).toBe(false);
    expect(summary.remainingDays).toBe(0);
    expect(summary.displayMessage).toBe('Trial expired');
  });

  it('persists EXPIRED status in DB when getAndSyncEntitlement detects elapsed trial', async () => {
    const updateMock = vi.fn().mockResolvedValue({});
    const mockDb = {
      subscriptionEntitlement: {
        findUnique: vi.fn().mockResolvedValue({
          id: 'ent-1',
          userId: 'user-1',
          status: EntitlementStatus.TRIAL,
          trialStartedAt: baseDate,
          trialExpiresAt: trialEnd,
          paidUntil: null,
          currentPlanId: null,
          recurringAuthorized: false,
        }),
        update: updateMock,
      },
    } as unknown as PrismaClient;

    const afterExpiry = new Date(trialEnd.getTime() + 60_000);
    const summary = await getAndSyncEntitlement('user-1', afterExpiry, mockDb);

    expect(summary.status).toBe(EntitlementStatus.EXPIRED);
    expect(summary.hasAccess).toBe(false);
    expect(updateMock).toHaveBeenCalledWith({
      where: { id: 'ent-1' },
      data: { status: EntitlementStatus.EXPIRED },
    });
  });

  it('grants access for ACTIVE paid subscription and expires when paidUntil elapses', () => {
    const paidUntil = new Date('2026-12-01T10:00:00.000Z');
    const active = computeEntitlementSummary(
      {
        status: EntitlementStatus.ACTIVE,
        trialStartedAt: baseDate,
        trialExpiresAt: trialEnd,
        paidUntil,
        currentPlanId: 'plan-1',
        recurringAuthorized: false,
      },
      new Date('2026-11-15T10:00:00.000Z'),
    );

    expect(active.status).toBe(EntitlementStatus.ACTIVE);
    expect(active.hasAccess).toBe(true);
    expect(active.isTrial).toBe(false);
    expect(active.remainingDays).toBe(16);

    const expired = computeEntitlementSummary(
      {
        status: EntitlementStatus.ACTIVE,
        trialStartedAt: baseDate,
        trialExpiresAt: trialEnd,
        paidUntil,
        currentPlanId: 'plan-1',
        recurringAuthorized: false,
      },
      new Date('2026-12-02T10:00:00.000Z'),
    );

    expect(expired.status).toBe(EntitlementStatus.EXPIRED);
    expect(expired.hasAccess).toBe(false);
    expect(expired.displayMessage).toBe('Subscription expired');
  });
});
