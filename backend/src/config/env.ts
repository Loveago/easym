import dotenv from 'dotenv';
import { z } from 'zod';

dotenv.config();

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().positive().default(4000),
  DATABASE_URL: z.string().min(1).default('postgresql://postgres:postgres@localhost:5432/easymomo?schema=public'),
  JWT_SECRET: z.string().min(16).default('dev-only-jwt-secret-change-in-production'),
  JWT_EXPIRES_IN: z.string().default('7d'),
  PAYSTACK_SECRET_KEY: z.string().default('sk_test_placeholder'),
  PAYSTACK_BASE_URL: z.string().url().default('https://api.paystack.co'),
  PAYSTACK_CALLBACK_URL: z.string().url().default('https://easymomo.app/payment/callback'),
  ADMIN_EMAIL: z.string().email().default('admin@easymomo.app'),
  ADMIN_PASSWORD: z.string().default('Admin@123456'),
  ADMIN_NAME: z.string().default('EasyMoMo Admin'),
  ADMIN_PHONE: z.string().default('0240000000'),
  CORS_ORIGIN: z.string().default('*'),
  RATE_LIMIT_WINDOW_MS: z.coerce.number().int().positive().default(15 * 60 * 1000),
  RATE_LIMIT_MAX: z.coerce.number().int().positive().default(200),
});

const parsed = envSchema.safeParse(process.env);
if (!parsed.success) {
  console.error('Invalid environment configuration:', parsed.error.flatten().fieldErrors);
  throw new Error('Invalid environment configuration');
}

export const env = parsed.data;
