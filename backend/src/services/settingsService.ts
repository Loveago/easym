import { PrismaClient } from '@prisma/client';
import { env } from '../config/env';
import { prisma as defaultPrisma } from '../config/prisma';

export interface SettingDefinition {
  key: string;
  label: string;
  isSecret: boolean;
  description: string;
  defaultValue: string;
}

export const SETTING_DEFINITIONS: SettingDefinition[] = [
  {
    key: 'PAYSTACK_SECRET_KEY',
    label: 'Paystack Secret Key',
    isSecret: true,
    description: 'Used server-side to initialize/verify Paystack payments and validate webhooks.',
    defaultValue: '',
  },
  {
    key: 'PAYSTACK_PUBLIC_KEY',
    label: 'Paystack Public Key',
    isSecret: false,
    description: 'Public Paystack key (pk_live_... or pk_test_...) for checkout clients.',
    defaultValue: '',
  },
  {
    key: 'PAYSTACK_CALLBACK_URL',
    label: 'Paystack Callback URL',
    isSecret: false,
    description: 'Deep link or web callback URL after Paystack Checkout.',
    defaultValue: env.PAYSTACK_CALLBACK_URL,
  },
  {
    key: 'TRIAL_DAYS',
    label: 'Free Trial Duration (Days)',
    isSecret: false,
    description: 'Number of free trial days granted to newly registered agents.',
    defaultValue: '7',
  },
  {
    key: 'SUPPORT_PHONE',
    label: 'Support Phone / WhatsApp',
    isSecret: false,
    description: 'Agent support phone number displayed in help & billing screens.',
    defaultValue: '',
  },
  {
    key: 'SUPPORT_EMAIL',
    label: 'Support Email',
    isSecret: false,
    description: 'Support email address for agent inquiries.',
    defaultValue: '',
  },
  {
    key: 'MAINTENANCE_MODE',
    label: 'Maintenance Mode',
    isSecret: false,
    description: 'Set to "true" to temporarily display maintenance notice to clients.',
    defaultValue: 'false',
  },
  {
    key: 'APP_MIN_VERSION',
    label: 'Minimum Android App Version',
    isSecret: false,
    description: 'Minimum recommended Android app versionCode.',
    defaultValue: '1',
  },
];

export function maskSecretValue(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return '';
  if (trimmed.length <= 8) return '••••••••';
  return `${trimmed.slice(0, 7)}••••••••${trimmed.slice(-4)}`;
}

export async function getEffectivePaystackConfig(db: PrismaClient = defaultPrisma): Promise<{
  secretKey: string;
  publicKey: string;
  callbackUrl: string;
}> {
  let dbSettings: Array<{ key: string; value: string }> = [];
  if (!(db === defaultPrisma && env.NODE_ENV === 'test')) {
    try {
      if ((db as unknown as { systemSetting?: { findMany?: unknown } })?.systemSetting?.findMany) {
        dbSettings = await db.systemSetting.findMany({
          where: {
            key: { in: ['PAYSTACK_SECRET_KEY', 'PAYSTACK_PUBLIC_KEY', 'PAYSTACK_CALLBACK_URL'] },
          },
          select: { key: true, value: true },
        });
      }
    } catch {
      // Fallback to env if system_settings table is not yet migrated or unavailable in unit tests
    }
  }

  const map = new Map(dbSettings.map((s) => [s.key, s.value.trim()]));
  return {
    secretKey: map.get('PAYSTACK_SECRET_KEY') || env.PAYSTACK_SECRET_KEY,
    publicKey: map.get('PAYSTACK_PUBLIC_KEY') || '',
    callbackUrl: map.get('PAYSTACK_CALLBACK_URL') || env.PAYSTACK_CALLBACK_URL,
  };
}

export async function listAdminSettings(db: PrismaClient = defaultPrisma) {
  const rows = await db.systemSetting.findMany();
  const byKey = new Map(rows.map((r) => [r.key, r]));

  return SETTING_DEFINITIONS.map((def) => {
    const row = byKey.get(def.key);
    const envFallback =
      def.key === 'PAYSTACK_SECRET_KEY' && env.PAYSTACK_SECRET_KEY !== 'sk_test_placeholder'
        ? env.PAYSTACK_SECRET_KEY
        : def.defaultValue;
    const rawValue = row?.value?.trim() ? row.value : envFallback;
    const isConfigured = Boolean(rawValue && rawValue.trim().length > 0);
    return {
      key: def.key,
      label: def.label,
      isSecret: def.isSecret,
      description: def.description,
      value: def.isSecret ? maskSecretValue(rawValue) : rawValue,
      isConfigured,
      source: row?.value?.trim() ? 'database' : envFallback ? 'env' : 'unset',
      updatedAt: row?.updatedAt ?? null,
    };
  });
}

export async function upsertAdminSettings(
  updates: Array<{ key: string; value: string }>,
  adminUserId: string,
  db: PrismaClient = defaultPrisma,
) {
  const allowedKeys = new Map(SETTING_DEFINITIONS.map((d) => [d.key, d]));

  for (const item of updates) {
    const def = allowedKeys.get(item.key);
    if (!def) continue;
    const trimmed = item.value.trim();
    // Skip masked placeholder values so saving other fields doesn't overwrite secrets
    if (def.isSecret && trimmed.includes('••••')) {
      continue;
    }
    await db.systemSetting.upsert({
      where: { key: item.key },
      update: {
        value: trimmed,
        isSecret: def.isSecret,
        description: def.description,
        updatedBy: adminUserId,
      },
      create: {
        key: item.key,
        value: trimmed,
        isSecret: def.isSecret,
        description: def.description,
        updatedBy: adminUserId,
      },
    });
  }

  return listAdminSettings(db);
}
