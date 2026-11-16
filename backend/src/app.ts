import cors from 'cors';
import express, { Request, Response } from 'express';
import rateLimit from 'express-rate-limit';
import helmet from 'helmet';
import { env } from './config/env';
import { errorHandler } from './middleware/errorHandler';
import adminRoutes from './routes/admin';
import adminPanelRoutes from './routes/adminPanel';
import authRoutes from './routes/auth';
import configRoutes from './routes/config';
import customerRoutes from './routes/customers';
import healthRoutes from './routes/health';
import reportRoutes from './routes/reports';
import subscriptionRoutes from './routes/subscriptions';
import transactionRoutes from './routes/transactions';
import webhookRoutes from './routes/webhooks';

export function createApp() {
  const app = express();
  app.set('trust proxy', 1);

  app.use(
    helmet({
      contentSecurityPolicy: {
        directives: {
          defaultSrc: ["'self'"],
          scriptSrc: ["'self'"],
          styleSrc: ["'self'", "'unsafe-inline'"],
        },
      },
    }),
  );
  app.use(
    cors({
      origin: env.CORS_ORIGIN === '*' ? true : env.CORS_ORIGIN.split(',').map((s) => s.trim()),
    }),
  );

  app.use(
    rateLimit({
      windowMs: env.RATE_LIMIT_WINDOW_MS,
      max: env.RATE_LIMIT_MAX,
      standardHeaders: true,
      legacyHeaders: false,
    }),
  );

  // Preserve rawBody for Paystack HMAC-SHA512 webhook signature verification
  app.use(
    express.json({
      limit: '1mb',
      verify: (req: Request, _res: Response, buf: Buffer) => {
        (req as unknown as { rawBody?: Buffer }).rawBody = buf;
      },
    }),
  );

  app.use('/', adminPanelRoutes);
  app.use('/api/health', healthRoutes);
  app.use('/api/auth', authRoutes);
  app.use('/api/subscriptions', subscriptionRoutes);
  app.use('/api/webhooks', webhookRoutes);
  app.use('/api/transactions', transactionRoutes);
  app.use('/api/customers', customerRoutes);
  app.use('/api/reports', reportRoutes);
  app.use('/api/config', configRoutes);
  app.use('/api/admin', adminRoutes);

  app.use((_req, res) => {
    res.status(404).json({
      error: {
        code: 'NOT_FOUND',
        message: 'Route not found',
      },
    });
  });

  app.use(errorHandler);
  return app;
}
