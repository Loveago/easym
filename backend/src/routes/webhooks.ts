import { Router } from 'express';
import { handlePaystackWebhook } from '../services/subscriptionService';

const router = Router();

router.post('/paystack', async (req, res, next) => {
  try {
    const rawBody = (req as unknown as { rawBody?: Buffer }).rawBody;
    const signature = req.header('x-paystack-signature');

    if (!rawBody) {
      res.status(400).json({ error: { code: 'MISSING_RAW_BODY', message: 'Raw body required' } });
      return;
    }

    const result = await handlePaystackWebhook(rawBody, signature, req.body);
    res.status(200).json(result);
  } catch (err) {
    next(err);
  }
});

export default router;
