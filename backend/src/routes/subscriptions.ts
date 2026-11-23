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

function escapeHtmlAttr(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

router.get('/callback', (req, res) => {
  const rawReference = typeof req.query.reference === 'string' ? req.query.reference.trim() : '';
  const rawTrxref = typeof req.query.trxref === 'string' ? req.query.trxref.trim() : '';
  const reference = rawReference || rawTrxref;

  const params = new URLSearchParams();
  if (reference) params.set('reference', reference);
  if (rawTrxref) params.set('trxref', rawTrxref);

  const query = params.toString();
  const appDeepLink = query ? `easymomo://payment/callback?${query}` : 'easymomo://payment/callback';
  const safeDeepLink = escapeHtmlAttr(appDeepLink);
  const safeReferenceText = reference ? escapeHtmlAttr(reference) : '';

  res
    .status(200)
    .type('html')
    .send(`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta http-equiv="refresh" content="0;url=${safeDeepLink}" />
  <title>Returning to EasyMoMo...</title>
  <style>
    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0b1412; color: #f3f7f5; display: flex; min-height: 100vh; align-items: center; justify-content: center; padding: 24px; }
    .card { max-width: 380px; width: 100%; background: #13221e; border: 1px solid #1e3b33; border-radius: 16px; padding: 28px 24px; text-align: center; box-sizing: border-box; }
    .badge { width: 56px; height: 56px; margin: 0 auto 16px; border-radius: 999px; background: #10b98122; color: #10b981; font-size: 28px; line-height: 56px; font-weight: 700; }
    h1 { margin: 0 0 8px; font-size: 20px; }
    p { margin: 0 0 20px; font-size: 14px; color: #a7bbb5; line-height: 1.5; }
    .ref { font-size: 12px; color: #7fa097; margin-bottom: 20px; word-break: break-all; }
    .btn { display: inline-block; width: 100%; box-sizing: border-box; background: #10b981; color: #052e1c; text-decoration: none; font-weight: 700; font-size: 15px; padding: 14px 18px; border-radius: 12px; }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">&#10003;</div>
    <h1>Payment Received</h1>
    <p>Redirecting you back to the EasyMoMo app to confirm your subscription...</p>
    ${safeReferenceText ? `<div class="ref">Reference: ${safeReferenceText}</div>` : ''}
    <a class="btn" href="${safeDeepLink}">Open EasyMoMo App</a>
  </div>
</body>
</html>`);
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
