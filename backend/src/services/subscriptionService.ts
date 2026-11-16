import crypto from 'crypto';
import { EntitlementStatus, PaymentStatus, Prisma, PrismaClient } from '@prisma/client';
import { env } from '../config/env';
import { prisma as defaultPrisma } from '../config/prisma';
import { AppError } from '../middleware/errorHandler';
import {
  PaystackGateway,
  paystackGateway as defaultGateway,
  verifyPaystackSignature,
} from './paystackService';
import { getEffectivePaystackConfig } from './settingsService';
import { computeEntitlementSummary } from './trialService';

const MS_PER_DAY = 24 * 60 * 60 * 1000;

export async function listActivePlans(db: PrismaClient = defaultPrisma) {
  const plans = await db.subscriptionPlan.findMany({
    where: { active: true },
    orderBy: { durationDays: 'asc' },
  });
  return plans.map((p) => ({
    id: p.id,
    code: p.code,
    name: p.name,
    price: Number(p.price),
    currency: p.currency,
    billingInterval: p.billingInterval,
    durationDays: p.durationDays,
    active: p.active,
  }));
}

export async function initializeSubscriptionPayment(
  userId: string,
  planId: string,
  db: PrismaClient = defaultPrisma,
  gateway: PaystackGateway = defaultGateway,
) {
  const [user, plan] = await Promise.all([
    db.user.findUnique({ where: { id: userId }, select: { id: true, email: true } }),
    db.subscriptionPlan.findUnique({ where: { id: planId } }),
  ]);

  if (!user) {
    throw new AppError(404, 'USER_NOT_FOUND', 'User not found');
  }
  if (!plan || !plan.active) {
    throw new AppError(404, 'PLAN_NOT_FOUND', 'Subscription plan not found or inactive');
  }

  const reference = `AM_${crypto.randomUUID().replace(/-/g, '').slice(0, 20)}`;
  const amountPesewas = Math.round(Number(plan.price) * 100);

  await db.payment.create({
    data: {
      userId: user.id,
      planId: plan.id,
      provider: 'PAYSTACK',
      reference,
      amount: plan.price,
      currency: plan.currency,
      status: PaymentStatus.PENDING,
    },
  });

  const { callbackUrl } = await getEffectivePaystackConfig(db);
  const paystackInit = await gateway.initializeTransaction({
    email: user.email,
    amountPesewas,
    currency: plan.currency,
    reference,
    callbackUrl,
    metadata: {
      userId: user.id,
      planId: plan.id,
      planCode: plan.code,
    },
  });

  return {
    authorizationUrl: paystackInit.authorizationUrl,
    accessCode: paystackInit.accessCode,
    reference: paystackInit.reference,
    amount: Number(plan.price),
    currency: plan.currency,
    plan: {
      id: plan.id,
      code: plan.code,
      name: plan.name,
      billingInterval: plan.billingInterval,
      durationDays: plan.durationDays,
    },
  };
}

export async function verifyAndActivateSubscription(
  reference: string,
  requestingUserId?: string,
  db: PrismaClient = defaultPrisma,
  gateway: PaystackGateway = defaultGateway,
) {
  const existingPayment = await db.payment.findUnique({
    where: { reference },
    include: { plan: true },
  });

  if (!existingPayment) {
    throw new AppError(404, 'PAYMENT_NOT_FOUND', 'Payment reference not found');
  }

  if (requestingUserId && existingPayment.userId !== requestingUserId) {
    throw new AppError(403, 'FORBIDDEN', 'You do not have access to this payment');
  }

  // Idempotent return if already verified and processed
  if (existingPayment.status === PaymentStatus.SUCCESS) {
    const entitlement = await db.subscriptionEntitlement.findUnique({
      where: { userId: existingPayment.userId },
    });
    return {
      alreadyProcessed: true,
      payment: {
        id: existingPayment.id,
        reference: existingPayment.reference,
        status: existingPayment.status,
        amount: Number(existingPayment.amount),
        currency: existingPayment.currency,
        channel: existingPayment.channel,
        paidAt: existingPayment.paidAt?.toISOString() ?? null,
      },
      entitlement: entitlement ? computeEntitlementSummary(entitlement) : null,
    };
  }

  const verified = await gateway.verifyTransaction(reference);

  if (verified.status !== 'success') {
    await db.payment.update({
      where: { id: existingPayment.id },
      data: {
        status: PaymentStatus.FAILED,
        channel: verified.channel,
        rawMetadata: verified.raw as Prisma.InputJsonValue,
      },
    });
    throw new AppError(400, 'PAYMENT_NOT_SUCCESSFUL', `Payment status is ${verified.status}`);
  }

  const expectedPesewas = Math.round(Number(existingPayment.amount) * 100);
  if (
    verified.reference !== existingPayment.reference ||
    verified.currency.toUpperCase() !== existingPayment.currency.toUpperCase() ||
    verified.amount < expectedPesewas
  ) {
    throw new AppError(400, 'PAYMENT_MISMATCH', 'Verified payment details do not match order');
  }

  const now = new Date();
  const paidAt = verified.paidAt ? new Date(verified.paidAt) : now;
  const isReusable = Boolean(
    verified.authorization?.reusable && verified.authorization?.authorizationCode,
  );

  const result = await db.$transaction(async (tx) => {
    // Re-check inside transaction for concurrency safety
    const lockedPayment = await tx.payment.findUnique({
      where: { id: existingPayment.id },
    });

    if (lockedPayment?.status === PaymentStatus.SUCCESS) {
      const ent = await tx.subscriptionEntitlement.findUnique({
        where: { userId: existingPayment.userId },
      });
      return { alreadyProcessed: true, payment: lockedPayment, entitlement: ent };
    }

    const updatedPayment = await tx.payment.update({
      where: { id: existingPayment.id },
      data: {
        status: PaymentStatus.SUCCESS,
        channel: verified.channel,
        paidAt,
        authorizationReusable: isReusable,
        rawMetadata: verified.raw as Prisma.InputJsonValue,
      },
    });

    const currentEntitlement = await tx.subscriptionEntitlement.findUnique({
      where: { userId: existingPayment.userId },
    });

    const baseDate =
      currentEntitlement?.paidUntil && currentEntitlement.paidUntil.getTime() > now.getTime()
        ? currentEntitlement.paidUntil
        : now;
    const newPaidUntil = new Date(
      baseDate.getTime() + existingPayment.plan.durationDays * MS_PER_DAY,
    );

    const updatedEntitlement = await tx.subscriptionEntitlement.upsert({
      where: { userId: existingPayment.userId },
      create: {
        userId: existingPayment.userId,
        status: EntitlementStatus.ACTIVE,
        paidUntil: newPaidUntil,
        currentPlanId: existingPayment.planId,
        recurringAuthorized: isReusable,
        authorizationCode: isReusable ? verified.authorization?.authorizationCode ?? null : null,
      },
      update: {
        status: EntitlementStatus.ACTIVE,
        paidUntil: newPaidUntil,
        currentPlanId: existingPayment.planId,
        recurringAuthorized: isReusable,
        authorizationCode: isReusable ? verified.authorization?.authorizationCode ?? null : null,
      },
    });

    return {
      alreadyProcessed: false,
      payment: updatedPayment,
      entitlement: updatedEntitlement,
    };
  });

  return {
    alreadyProcessed: result.alreadyProcessed,
    payment: {
      id: result.payment.id,
      reference: result.payment.reference,
      status: result.payment.status,
      amount: Number(result.payment.amount),
      currency: result.payment.currency,
      channel: result.payment.channel,
      paidAt: result.payment.paidAt?.toISOString() ?? null,
    },
    entitlement: result.entitlement ? computeEntitlementSummary(result.entitlement, now) : null,
  };
}

export async function handlePaystackWebhook(
  rawBody: Buffer,
  signature: string | undefined,
  payload: { event?: string; data?: { reference?: string; channel?: string } },
  db: PrismaClient = defaultPrisma,
  gateway: PaystackGateway = defaultGateway,
) {
  const { secretKey } = await getEffectivePaystackConfig(db);
  if (!verifyPaystackSignature(rawBody, signature, secretKey)) {
    throw new AppError(401, 'INVALID_WEBHOOK_SIGNATURE', 'Invalid Paystack webhook signature');
  }

  const event = payload.event ?? '';
  const reference = payload.data?.reference;

  if (event === 'charge.success' && reference) {
    const result = await verifyAndActivateSubscription(reference, undefined, db, gateway);
    return { received: true, event, processed: true, alreadyProcessed: result.alreadyProcessed };
  }

  if (event === 'charge.failed' && reference) {
    await db.payment.updateMany({
      where: { reference, status: PaymentStatus.PENDING },
      data: {
        status: PaymentStatus.FAILED,
        channel: payload.data?.channel ?? null,
      },
    });
    return { received: true, event, processed: true };
  }

  return { received: true, event, processed: false };
}

export async function getPaymentHistory(userId: string, db: PrismaClient = defaultPrisma) {
  const payments = await db.payment.findMany({
    where: { userId },
    include: {
      plan: {
        select: { id: true, code: true, name: true, billingInterval: true },
      },
    },
    orderBy: { createdAt: 'desc' },
  });

  return payments.map((p) => ({
    id: p.id,
    reference: p.reference,
    amount: Number(p.amount),
    currency: p.currency,
    channel: p.channel,
    status: p.status,
    authorizationReusable: p.authorizationReusable,
    paidAt: p.paidAt?.toISOString() ?? null,
    createdAt: p.createdAt.toISOString(),
    plan: p.plan,
  }));
}
