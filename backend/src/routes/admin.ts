import { Router } from 'express';
import { AccountStatus, BillingInterval, NetworkType, UserRole } from '@prisma/client';
import { z } from 'zod';
import { prisma } from '../config/prisma';
import { requireAdmin, requireAuth } from '../middleware/auth';
import { validate } from '../middleware/validate';
import {
  createDataBundle,
  grantTrialExtension,
  publishUssdFlow,
  rollbackUssdFlow,
  updateUserAccountStatus,
  upsertNetworkProfile,
  upsertSubscriptionPlan,
} from '../services/configService';
import { listAdminSettings, upsertAdminSettings } from '../services/settingsService';

const router = Router();

router.use(requireAuth, requireAdmin);

const publishFlowSchema = z.object({
  network: z.nativeEnum(NetworkType),
  transactionType: z.string().min(2).max(64),
  configuration: z.record(z.unknown()),
  enabled: z.boolean().default(false),
  markVerified: z.boolean().default(false),
});

const rollbackFlowSchema = z.object({
  network: z.nativeEnum(NetworkType),
  transactionType: z.string().min(2).max(64),
  targetVersion: z.number().int().positive(),
});

const networkProfileSchema = z.object({
  network: z.nativeEnum(NetworkType),
  configuration: z.record(z.unknown()),
});

const createBundleSchema = z.object({
  network: z.nativeEnum(NetworkType),
  name: z.string().min(1).max(120),
  allowance: z.string().min(1).max(64),
  price: z.number().positive(),
  validity: z.string().min(1).max(64),
  providerOption: z.string().min(1).max(64),
  active: z.boolean().default(true),
});

const userStatusSchema = z.object({
  accountStatus: z.nativeEnum(AccountStatus),
});

const trialExtendSchema = z.object({
  extraDays: z.number().int().min(1).max(90),
});

const upsertPlanSchema = z.object({
  code: z.string().min(2).max(32),
  name: z.string().min(2).max(120),
  price: z.number().positive(),
  currency: z.string().length(3).default('GHS'),
  billingInterval: z.nativeEnum(BillingInterval),
  durationDays: z.number().int().min(1).max(366),
  active: z.boolean().default(true),
});

router.post('/ussd-flows/publish', validate(publishFlowSchema), async (req, res, next) => {
  try {
    const flow = await publishUssdFlow(req.body);
    res.status(201).json({ flow });
  } catch (err) {
    next(err);
  }
});

router.post('/ussd-flows/rollback', validate(rollbackFlowSchema), async (req, res, next) => {
  try {
    const { network, transactionType, targetVersion } = req.body;
    const flow = await rollbackUssdFlow(network, transactionType, targetVersion);
    res.status(200).json({ flow });
  } catch (err) {
    next(err);
  }
});

router.put('/networks', validate(networkProfileSchema), async (req, res, next) => {
  try {
    const profile = await upsertNetworkProfile(req.body.network, req.body.configuration);
    res.status(200).json({ network: profile });
  } catch (err) {
    next(err);
  }
});

router.post('/bundles', validate(createBundleSchema), async (req, res, next) => {
  try {
    const bundle = await createDataBundle(req.body);
    res.status(201).json({ bundle });
  } catch (err) {
    next(err);
  }
});

router.patch('/users/:id/status', validate(userStatusSchema), async (req, res, next) => {
  try {
    const user = await updateUserAccountStatus(String(req.params.id), req.body.accountStatus);
    res.status(200).json({ user });
  } catch (err) {
    next(err);
  }
});

router.post('/users/:id/trial-extend', validate(trialExtendSchema), async (req, res, next) => {
  try {
    const entitlement = await grantTrialExtension(String(req.params.id), req.body.extraDays);
    res.status(200).json({ entitlement });
  } catch (err) {
    next(err);
  }
});

router.put('/plans', validate(upsertPlanSchema), async (req, res, next) => {
  try {
    const plan = await upsertSubscriptionPlan(req.body);
    res.status(200).json({ plan });
  } catch (err) {
    next(err);
  }
});

// --- Feature-rich Admin Panel APIs ---

router.get('/overview', async (_req, res, next) => {
  try {
    const [
      totalAgents,
      activeAgents,
      suspendedAgents,
      trialCount,
      paidCount,
      expiredCount,
      totalTransactions,
      successfulTxAgg,
      successfulPaymentsAgg,
      recentUsers,
      recentTransactions,
    ] = await Promise.all([
      prisma.user.count(),
      prisma.user.count({ where: { accountStatus: 'ACTIVE' } }),
      prisma.user.count({ where: { accountStatus: 'SUSPENDED' } }),
      prisma.subscriptionEntitlement.count({ where: { status: 'TRIAL' } }),
      prisma.subscriptionEntitlement.count({ where: { status: 'ACTIVE' } }),
      prisma.subscriptionEntitlement.count({ where: { status: 'EXPIRED' } }),
      prisma.transaction.count(),
      prisma.transaction.aggregate({
        where: { status: 'SUCCESSFUL' },
        _sum: { amount: true },
        _count: true,
      }),
      prisma.payment.aggregate({
        where: { status: 'SUCCESS' },
        _sum: { amount: true },
        _count: true,
      }),
      prisma.user.findMany({
        orderBy: { createdAt: 'desc' },
        take: 6,
        select: {
          id: true,
          fullName: true,
          email: true,
          phone: true,
          role: true,
          accountStatus: true,
          createdAt: true,
          entitlement: {
            select: { status: true, trialExpiresAt: true, paidUntil: true },
          },
        },
      }),
      prisma.transaction.findMany({
        orderBy: { createdAt: 'desc' },
        take: 8,
        include: {
          user: { select: { fullName: true, phone: true } },
        },
      }),
    ]);

    res.status(200).json({
      kpis: {
        totalAgents,
        activeAgents,
        suspendedAgents,
        trialCount,
        paidCount,
        expiredCount,
        totalTransactions,
        successfulTransactions: successfulTxAgg._count,
        successfulTransactionVolumeGhs: Number(successfulTxAgg._sum.amount ?? 0),
        successfulPaymentsCount: successfulPaymentsAgg._count,
        subscriptionRevenueGhs: Number(successfulPaymentsAgg._sum.amount ?? 0),
      },
      recentUsers,
      recentTransactions,
    });
  } catch (err) {
    next(err);
  }
});

router.get('/users', async (req, res, next) => {
  try {
    const q = String(req.query.q ?? '').trim();
    const status = String(req.query.status ?? '').trim();

    const where: Record<string, unknown> = {};
    if (status === 'ACTIVE' || status === 'SUSPENDED') {
      where.accountStatus = status;
    }
    if (q) {
      where.OR = [
        { fullName: { contains: q, mode: 'insensitive' } },
        { email: { contains: q, mode: 'insensitive' } },
        { phone: { contains: q, mode: 'insensitive' } },
      ];
    }

    const users = await prisma.user.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      take: 100,
      select: {
        id: true,
        fullName: true,
        email: true,
        phone: true,
        role: true,
        accountStatus: true,
        createdAt: true,
        entitlement: true,
        _count: { select: { transactions: true, payments: true } },
      },
    });
    res.status(200).json({ users });
  } catch (err) {
    next(err);
  }
});

const userRoleSchema = z.object({
  role: z.nativeEnum(UserRole),
});

router.patch('/users/:id/role', validate(userRoleSchema), async (req, res, next) => {
  try {
    const user = await prisma.user.update({
      where: { id: String(req.params.id) },
      data: { role: req.body.role },
      select: { id: true, fullName: true, email: true, role: true },
    });
    res.status(200).json({ user });
  } catch (err) {
    next(err);
  }
});

router.get('/transactions', async (req, res, next) => {
  try {
    const network = String(req.query.network ?? '').trim();
    const status = String(req.query.status ?? '').trim();
    const where: Record<string, unknown> = {};
    if (network) where.network = network;
    if (status) where.status = status;

    const transactions = await prisma.transaction.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      take: 100,
      include: {
        user: { select: { fullName: true, email: true, phone: true } },
      },
    });
    res.status(200).json({ transactions });
  } catch (err) {
    next(err);
  }
});

router.get('/payments', async (_req, res, next) => {
  try {
    const payments = await prisma.payment.findMany({
      orderBy: { createdAt: 'desc' },
      take: 100,
      include: {
        user: { select: { fullName: true, email: true, phone: true } },
        plan: { select: { code: true, name: true } },
      },
    });
    res.status(200).json({ payments });
  } catch (err) {
    next(err);
  }
});

router.get('/plans', async (_req, res, next) => {
  try {
    const plans = await prisma.subscriptionPlan.findMany({
      orderBy: { price: 'asc' },
    });
    res.status(200).json({ plans });
  } catch (err) {
    next(err);
  }
});

router.get('/ussd-flows', async (_req, res, next) => {
  try {
    const flows = await prisma.ussdFlowProfile.findMany({
      orderBy: [{ network: 'asc' }, { transactionType: 'asc' }, { version: 'desc' }],
    });
    res.status(200).json({ flows });
  } catch (err) {
    next(err);
  }
});

router.get('/bundles', async (_req, res, next) => {
  try {
    const bundles = await prisma.dataBundle.findMany({
      orderBy: [{ network: 'asc' }, { price: 'asc' }],
    });
    res.status(200).json({ bundles });
  } catch (err) {
    next(err);
  }
});

const toggleBundleSchema = z.object({
  active: z.boolean(),
});

router.patch('/bundles/:id', validate(toggleBundleSchema), async (req, res, next) => {
  try {
    const bundle = await prisma.dataBundle.update({
      where: { id: String(req.params.id) },
      data: { active: req.body.active },
    });
    res.status(200).json({ bundle });
  } catch (err) {
    next(err);
  }
});

router.get('/settings', async (_req, res, next) => {
  try {
    const settings = await listAdminSettings();
    res.status(200).json({ settings });
  } catch (err) {
    next(err);
  }
});

const updateSettingsSchema = z.object({
  settings: z
    .array(
      z.object({
        key: z.string().min(1),
        value: z.string(),
      }),
    )
    .min(1),
});

router.put('/settings', validate(updateSettingsSchema), async (req, res, next) => {
  try {
    const adminId = req.user!.id;
    const settings = await upsertAdminSettings(req.body.settings, adminId);
    res.status(200).json({ settings });
  } catch (err) {
    next(err);
  }
});

export default router;
