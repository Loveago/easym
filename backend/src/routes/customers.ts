import { Router } from 'express';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth';
import { requireActiveEntitlement } from '../middleware/entitlement';
import { validate } from '../middleware/validate';
import {
  clearCustomersForAgent,
  deleteCustomerForAgent,
  getCustomerSyncStatus,
  listCustomersForAgent,
  syncCustomerBatch,
} from '../services/customerService';

const router = Router();

// Strict account isolation: every route requires verified JWT and scopes strictly to req.user!.id
router.use(requireAuth, requireActiveEntitlement);

const customerSyncItemSchema = z
  .object({
    cloudId: z.string().max(128).nullable().optional(),
    phoneNumber: z.string().min(7).max(32),
    normalizedPhoneNumber: z.string().min(7).max(32).nullable().optional(),
    nickname: z.string().max(120).nullable().optional(),
    firstUsedAt: z.number().int().nonnegative(),
    lastUsedAt: z.number().int().nonnegative(),
    usageCount: z.number().int().min(1).max(1000000),
    updatedAt: z.number().int().nonnegative(),
    deletedAt: z.number().int().nonnegative().nullable().optional(),
  })
  .strict();

const syncBatchSchema = z.object({
  customers: z.array(customerSyncItemSchema).min(1).max(200),
});

router.get('/', async (req, res, next) => {
  try {
    const sinceMs = req.query.since ? Number(req.query.since) : undefined;
    const cursor = req.query.cursor ? String(req.query.cursor) : undefined;
    const limit = req.query.limit ? Number(req.query.limit) : 100;
    const includeDeleted = req.query.includeDeleted === 'true';

    const result = await listCustomersForAgent(req.user!.id, {
      sinceMs: Number.isFinite(sinceMs) ? sinceMs : undefined,
      cursor,
      limit: Number.isFinite(limit) ? limit : 100,
      includeDeleted,
    });
    res.status(200).json(result);
  } catch (err) {
    next(err);
  }
});

router.get('/status', async (req, res, next) => {
  try {
    const status = await getCustomerSyncStatus(req.user!.id);
    res.status(200).json(status);
  } catch (err) {
    next(err);
  }
});

router.post('/sync', validate(syncBatchSchema), async (req, res, next) => {
  try {
    const result = await syncCustomerBatch(req.user!.id, req.body.customers);
    res.status(200).json(result);
  } catch (err) {
    next(err);
  }
});

router.put('/:normalizedPhone', validate(customerSyncItemSchema), async (req, res, next) => {
  try {
    const result = await syncCustomerBatch(req.user!.id, [
      {
        ...req.body,
        normalizedPhoneNumber: String(req.params.normalizedPhone),
      },
    ]);
    res.status(200).json({ customer: result.synced[0], serverTime: result.serverTime });
  } catch (err) {
    next(err);
  }
});

router.delete('/:id', async (req, res, next) => {
  try {
    const customer = await deleteCustomerForAgent(req.user!.id, String(req.params.id));
    res.status(200).json({ customer });
  } catch (err) {
    next(err);
  }
});

router.delete('/', async (req, res, next) => {
  try {
    const result = await clearCustomersForAgent(req.user!.id);
    res.status(200).json(result);
  } catch (err) {
    next(err);
  }
});

export default router;
