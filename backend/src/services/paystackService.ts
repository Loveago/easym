import crypto from 'crypto';
import { env } from '../config/env';
import { AppError } from '../middleware/errorHandler';
import { getEffectivePaystackConfig } from './settingsService';

export interface PaystackInitializeInput {
  email: string;
  amountPesewas: number;
  currency: string;
  reference: string;
  callbackUrl: string;
  metadata?: Record<string, unknown>;
}

export interface PaystackInitializeResult {
  authorizationUrl: string;
  accessCode: string;
  reference: string;
}

export interface PaystackVerifyResult {
  reference: string;
  status: string;
  amount: number; // in pesewas
  currency: string;
  channel: string | null;
  paidAt: string | null;
  authorization: {
    reusable: boolean;
    authorizationCode: string | null;
  } | null;
  raw: Record<string, unknown>;
}

export interface PaystackGateway {
  initializeTransaction(input: PaystackInitializeInput): Promise<PaystackInitializeResult>;
  verifyTransaction(reference: string): Promise<PaystackVerifyResult>;
}

export function verifyPaystackSignature(
  rawBody: Buffer,
  signatureHeader: string | undefined,
  secretKey: string = env.PAYSTACK_SECRET_KEY,
): boolean {
  if (!signatureHeader || !rawBody) return false;
  const expected = crypto.createHmac('sha512', secretKey).update(rawBody).digest('hex');
  const expectedBuf = Buffer.from(expected, 'utf8');
  const actualBuf = Buffer.from(signatureHeader.trim(), 'utf8');
  if (expectedBuf.length !== actualBuf.length) return false;
  return crypto.timingSafeEqual(expectedBuf, actualBuf);
}

export const paystackGateway: PaystackGateway = {
  async initializeTransaction(input: PaystackInitializeInput): Promise<PaystackInitializeResult> {
    const { secretKey } = await getEffectivePaystackConfig();
    const response = await fetch(`${env.PAYSTACK_BASE_URL}/transaction/initialize`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${secretKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email: input.email,
        amount: input.amountPesewas,
        currency: input.currency,
        reference: input.reference,
        callback_url: input.callbackUrl,
        metadata: input.metadata,
      }),
    });

    const json = (await response.json()) as {
      status: boolean;
      message?: string;
      data?: { authorization_url: string; access_code: string; reference: string };
    };

    if (!response.ok || !json.status || !json.data) {
      throw new AppError(
        502,
        'PAYSTACK_INIT_FAILED',
        json.message || 'Failed to initialize Paystack transaction',
      );
    }

    return {
      authorizationUrl: json.data.authorization_url,
      accessCode: json.data.access_code,
      reference: json.data.reference,
    };
  },

  async verifyTransaction(reference: string): Promise<PaystackVerifyResult> {
    const { secretKey } = await getEffectivePaystackConfig();
    const response = await fetch(
      `${env.PAYSTACK_BASE_URL}/transaction/verify/${encodeURIComponent(reference)}`,
      {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${secretKey}`,
        },
      },
    );

    const json = (await response.json()) as {
      status: boolean;
      message?: string;
      data?: {
        reference: string;
        status: string;
        amount: number;
        currency: string;
        channel?: string;
        paid_at?: string;
        authorization?: {
          reusable?: boolean;
          authorization_code?: string;
        };
      };
    };

    if (!response.ok || !json.status || !json.data) {
      throw new AppError(
        502,
        'PAYSTACK_VERIFY_FAILED',
        json.message || 'Failed to verify Paystack transaction',
      );
    }

    return {
      reference: json.data.reference,
      status: json.data.status,
      amount: json.data.amount,
      currency: json.data.currency,
      channel: json.data.channel ?? null,
      paidAt: json.data.paid_at ?? null,
      authorization: json.data.authorization
        ? {
            reusable: Boolean(json.data.authorization.reusable),
            authorizationCode: json.data.authorization.authorization_code ?? null,
          }
        : null,
      raw: json.data as unknown as Record<string, unknown>,
    };
  },
};
