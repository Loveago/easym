import { Router } from 'express';
import { NetworkType } from '@prisma/client';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth';
import { requireActiveEntitlement } from '../middleware/entitlement';
import { validate } from '../middleware/validate';
import {
  getActiveDataBundles,
  getActiveUssdFlows,
  getNetworkProfiles,
} from '../services/configService';

const router = Router();

const networkQuerySchema = z.object({
  network: z.nativeEnum(NetworkType).optional(),
});

router.get('/ussd-flows', requireAuth, requireActiveEntitlement, validate(networkQuerySchema, 'query'), async (req, res, next) => {
  try {
    const flows = await getActiveUssdFlows(req.query.network as NetworkType | undefined);
    res.status(200).json({ flows });
  } catch (err) {
    next(err);
  }
});

router.get('/networks', requireAuth, requireActiveEntitlement, async (_req, res, next) => {
  try {
    const networks = await getNetworkProfiles();
    res.status(200).json({ networks });
  } catch (err) {
    next(err);
  }
});

router.get('/bundles', requireAuth, requireActiveEntitlement, validate(networkQuerySchema, 'query'), async (req, res, next) => {
  try {
    const bundles = await getActiveDataBundles(req.query.network as NetworkType | undefined);
    res.status(200).json({ bundles });
  } catch (err) {
    next(err);
  }
});

export default router;
