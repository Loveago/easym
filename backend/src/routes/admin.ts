import { Router } from 'express';
import { AccountStatus, BillingInterval, NetworkType } from '@prisma/client';
import { z } from 'zod';
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

export default router;
