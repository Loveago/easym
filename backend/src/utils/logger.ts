const SENSITIVE_KEYS = new Set([
  'password',
  'passwordhash',
  'password_hash',
  'pin',
  'secret',
  'token',
  'accesstoken',
  'authorization',
  'authorizationcode',
  'authorization_code',
  'paystack_secret_key',
  'jwt_secret',
  'x-paystack-signature',
]);

export function redactSensitive(value: unknown): unknown {
  if (value === null || value === undefined) return value;
  if (typeof value === 'string') {
    return value.replace(/\b(Bearer\s+)[A-Za-z0-9\-._~+/]+=*/gi, '$1[REDACTED]');
  }
  if (Array.isArray(value)) {
    return value.map(redactSensitive);
  }
  if (typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (SENSITIVE_KEYS.has(k.toLowerCase())) {
        out[k] = '[REDACTED]';
      } else {
        out[k] = redactSensitive(v);
      }
    }
    return out;
  }
  return value;
}

export const logger = {
  info(message: string, meta?: Record<string, unknown>) {
    console.log(
      JSON.stringify({
        level: 'info',
        time: new Date().toISOString(),
        message,
        ...(meta ? { meta: redactSensitive(meta) } : {}),
      }),
    );
  },
  warn(message: string, meta?: Record<string, unknown>) {
    console.warn(
      JSON.stringify({
        level: 'warn',
        time: new Date().toISOString(),
        message,
        ...(meta ? { meta: redactSensitive(meta) } : {}),
      }),
    );
  },
  error(message: string, meta?: Record<string, unknown>) {
    console.error(
      JSON.stringify({
        level: 'error',
        time: new Date().toISOString(),
        message,
        ...(meta ? { meta: redactSensitive(meta) } : {}),
      }),
    );
  },
};
