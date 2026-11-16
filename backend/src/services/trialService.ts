import { EntitlementStatus, PrismaClient, SubscriptionEntitlement } from '@prisma/client';
import { prisma as defaultPrisma } from '../config/prisma';

export const TRIAL_DURATION_DAYS = 7;
const MS_PER_DAY = 24 * 60 * 60 * 1000;

export interface EntitlementSummary {
  status: EntitlementStatus;
  hasAccess: boolean;
  isTrial: boolean;
  trialStartedAt: string | null;
  trialExpiresAt: string | null;
  paidUntil: string | null;
  currentPlanId: string | null;
  recurringAuthorized: boolean;
  remainingDays: number;
  displayMessage: string;
  serverTimeUtc: string;
}

export function computeEntitlementSummary(
  entitlement: Pick<
    SubscriptionEntitlement,
    | 'status'
    | 'trialStartedAt'
    | 'trialExpiresAt'
    | 'paidUntil'
    | 'currentPlanId'
    | 'recurringAuthorized'
  >,
  now: Date = new Date(),
): EntitlementSummary {
  const nowMs = now.getTime();
  let effectiveStatus = entitlement.status;
  let expiresAt: Date | null = null;

  if (entitlement.status === EntitlementStatus.TRIAL && entitlement.trialExpiresAt) {
    expiresAt = entitlement.trialExpiresAt;
    if (nowMs >= entitlement.trialExpiresAt.getTime()) {
      effectiveStatus = EntitlementStatus.EXPIRED;
    }
  } else if (entitlement.status === EntitlementStatus.ACTIVE && entitlement.paidUntil) {
    expiresAt = entitlement.paidUntil;
    if (nowMs >= entitlement.paidUntil.getTime()) {
      effectiveStatus = EntitlementStatus.EXPIRED;
    }
  }

  const hasAccess =
    effectiveStatus === EntitlementStatus.TRIAL || effectiveStatus === EntitlementStatus.ACTIVE;

  let remainingDays = 0;
  if (hasAccess && expiresAt) {
    const diffMs = Math.max(0, expiresAt.getTime() - nowMs);
    remainingDays = Math.ceil(diffMs / MS_PER_DAY);
  }

  let displayMessage = 'Subscription inactive';
  if (effectiveStatus === EntitlementStatus.TRIAL) {
    if (remainingDays <= 1 && expiresAt && expiresAt.getTime() - nowMs < MS_PER_DAY) {
      displayMessage = 'Trial ends today';
    } else {
      displayMessage = `${remainingDays} days remaining`;
    }
  } else if (effectiveStatus === EntitlementStatus.ACTIVE) {
    displayMessage = `Active subscription (${remainingDays} days remaining)`;
  } else if (effectiveStatus === EntitlementStatus.EXPIRED) {
    displayMessage = entitlement.paidUntil ? 'Subscription expired' : 'Trial expired';
  } else if (effectiveStatus === EntitlementStatus.CANCELLED) {
    displayMessage = 'Subscription cancelled';
  }

  return {
    status: effectiveStatus,
    hasAccess,
    isTrial: effectiveStatus === EntitlementStatus.TRIAL,
    trialStartedAt: entitlement.trialStartedAt?.toISOString() ?? null,
    trialExpiresAt: entitlement.trialExpiresAt?.toISOString() ?? null,
    paidUntil: entitlement.paidUntil?.toISOString() ?? null,
    currentPlanId: entitlement.currentPlanId ?? null,
    recurringAuthorized: entitlement.recurringAuthorized,
    remainingDays,
    displayMessage,
    serverTimeUtc: now.toISOString(),
  };
}

export async function getAndSyncEntitlement(
  userId: string,
  now: Date = new Date(),
  db: PrismaClient = defaultPrisma,
): Promise<EntitlementSummary> {
  const entitlement = await db.subscriptionEntitlement.findUnique({
    where: { userId },
  });

  if (!entitlement) {
    return {
      status: EntitlementStatus.EXPIRED,
      hasAccess: false,
      isTrial: false,
      trialStartedAt: null,
      trialExpiresAt: null,
      paidUntil: null,
      currentPlanId: null,
      recurringAuthorized: false,
      remainingDays: 0,
      displayMessage: 'No active subscription or trial',
      serverTimeUtc: now.toISOString(),
    };
  }

  const summary = computeEntitlementSummary(entitlement, now);

  // Persist EXPIRED transition server-side if time has elapsed
  if (summary.status === EntitlementStatus.EXPIRED && entitlement.status !== EntitlementStatus.EXPIRED) {
    await db.subscriptionEntitlement.update({
      where: { id: entitlement.id },
      data: { status: EntitlementStatus.EXPIRED },
    });
  }

  return summary;
}
