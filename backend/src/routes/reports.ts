import { Router } from 'express';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth';
import { requireActiveEntitlement } from '../middleware/entitlement';
import { validate } from '../middleware/validate';
import { getReportSummary, ReportPeriod } from '../services/reportService';

const router = Router();

const reportQuerySchema = z.object({
  period: z.enum(['daily', 'weekly', 'monthly']).default('daily'),
  date: z.string().datetime().optional(),
});

router.get('/summary', requireAuth, requireActiveEntitlement, validate(reportQuerySchema, 'query'), async (req, res, next) => {
  try {
    const period = (req.query.period as ReportPeriod) || 'daily';
    const refDate = req.query.date ? new Date(String(req.query.date)) : new Date();
    const summary = await getReportSummary(req.user!.id, period, refDate);
    res.status(200).json(summary);
  } catch (err) {
    next(err);
  }
});

export default router;
