import { Router } from 'express';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth';
import { validate } from '../middleware/validate';
import {
  getPaymentHistory,
  initializeSubscriptionPayment,
  listActivePlans,
  verifyAndActivateSubscription,
} from '../services/subscriptionService';
import { getAndSyncEntitlement } from '../services/trialService';

const router = Router();

const initSchema = z.object({
  planId: z.string().uuid(),
});

router.get('/plans', async (_req, res, next) => {
  try {
    const plans = await listActivePlans();
    res.status(200).json({ plans });
  } catch (err) {
    next(err);
  }
});

router.get('/status', requireAuth, async (req, res, next) => {
  try {
    const entitlement = await getAndSyncEntitlement(req.user!.id);
    res.status(200).json({ entitlement });
  } catch (err) {
    next(err);
  }
});

router.post('/initialize', requireAuth, validate(initSchema), async (req, res, next) => {
  try {
    const result = await initializeSubscriptionPayment(req.user!.id, req.body.planId);
    res.status(200).json(result);
  } catch (err) {
    next(err);
  }
});

router.get('/verify/:reference', requireAuth, async (req, res, next) => {
  try {
    const result = await verifyAndActivateSubscription(String(req.params.reference), req.user!.id);
    res.status(200).json(result);
  } catch (err) {
    next(err);
  }
});

router.get('/payments', requireAuth, async (req, res, next) => {
  try {
    const payments = await getPaymentHistory(req.user!.id);
    res.status(200).json({ payments });
  } catch (err) {
    next(err);
  }
});

export default router;
