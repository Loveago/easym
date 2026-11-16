import { Router } from 'express';
import {
  NetworkType,
  ProcessingType,
  TransactionStatus,
  TransactionType,
} from '@prisma/client';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth';
import { requireActiveEntitlement } from '../middleware/entitlement';
import { validate } from '../middleware/validate';
import {
  getTransactionById,
  listTransactions,
  syncTransactions,
} from '../services/transactionService';

const router = Router();

const eventSchema = z
  .object({
    eventType: z.string().min(1).max(64),
    previousStatus: z.nativeEnum(TransactionStatus).nullable().optional(),
    newStatus: z.nativeEnum(TransactionStatus),
    details: z.string().max(1000).nullable().optional(),
    createdAt: z.string().datetime().optional(),
  })
  .strict();

const syncItemSchema = z
  .object({
    clientTransactionId: z.string().min(1).max(128),
    providerReference: z.string().max(128).nullable().optional(),
    network: z.nativeEnum(NetworkType),
    transactionType: z.nativeEnum(TransactionType),
    processingType: z.nativeEnum(ProcessingType),
    recipientNumber: z.string().min(3).max(32),
    amount: z.number().nonnegative(),
    bundleId: z.string().uuid().nullable().optional(),
    bundleName: z.string().max(120).nullable().optional(),
    status: z.nativeEnum(TransactionStatus),
    confirmationSource: z.string().max(64).nullable().optional(),
    providerResponse: z.string().max(2000).nullable().optional(),
    createdAt: z.string().datetime().optional(),
    updatedAt: z.string().datetime().optional(),
    events: z.array(eventSchema).max(50).optional(),
  })
  .strict();

const syncBatchSchema = z.object({
  transactions: z.array(syncItemSchema).min(1).max(100),
});

const listQuerySchema = z.object({
  search: z.string().max(100).optional(),
  network: z.nativeEnum(NetworkType).optional(),
  transactionType: z.nativeEnum(TransactionType).optional(),
  status: z.nativeEnum(TransactionStatus).optional(),
  fromDate: z.string().datetime().optional(),
  toDate: z.string().datetime().optional(),
  page: z.coerce.number().int().min(1).default(1),
  limit: z.coerce.number().int().min(1).max(100).default(25),
});

// Syncing new/updated transactions requires active trial or paid subscription
router.post(
  '/sync',
  requireAuth,
  requireActiveEntitlement,
  validate(syncBatchSchema),
  async (req, res, next) => {
    try {
      const result = await syncTransactions(req.user!.id, req.body.transactions);
      res.status(200).json(result);
    } catch (err) {
      next(err);
    }
  },
);

// Transaction history remains readable even after subscription expiration
router.get('/', requireAuth, validate(listQuerySchema, 'query'), async (req, res, next) => {
  try {
    const result = await listTransactions(req.user!.id, req.query as never);
    res.status(200).json(result);
  } catch (err) {
    next(err);
  }
});

router.get('/:id', requireAuth, async (req, res, next) => {
  try {
    const result = await getTransactionById(req.user!.id, String(req.params.id));
    res.status(200).json({ transaction: result });
  } catch (err) {
    next(err);
  }
});

export default router;
