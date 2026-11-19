import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { AccountStatus, EntitlementStatus, PrismaClient, UserRole } from '@prisma/client';
import { env } from '../config/env';
import { prisma as defaultPrisma } from '../config/prisma';
import { AppError } from '../middleware/errorHandler';
import {
  EntitlementSummary,
  computeEntitlementSummary,
  getAndSyncEntitlement,
} from './trialService';
import { verifyAndRegisterDevice } from './deviceLimitService';
import { getEffectiveGoogleClientId, getEffectiveTrialDays } from './settingsService';

const BCRYPT_ROUNDS = 12;

export interface RegisterInput {
  fullName: string;
  email: string;
  phone: string;
  password: string;
  deviceId?: string | null;
  deviceName?: string | null;
}

export interface LoginInput {
  identifier: string; // email or phone
  password: string;
  deviceId?: string | null;
  deviceName?: string | null;
}

export interface AuthResponse {
  token: string;
  user: {
    id: string;
    fullName: string;
    email: string;
    phone: string;
    role: UserRole;
    accountStatus: AccountStatus;
    createdAt: string;
  };
  entitlement: EntitlementSummary;
}

function signToken(userId: string, role: UserRole): string {
  return jwt.sign({ sub: userId, role }, env.JWT_SECRET, {
    expiresIn: env.JWT_EXPIRES_IN as jwt.SignOptions['expiresIn'],
  });
}

export async function registerAgent(
  input: RegisterInput,
  db: PrismaClient = defaultPrisma,
): Promise<AuthResponse> {
  const email = input.email.trim().toLowerCase();
  const phone = input.phone.trim();

  const existing = await db.user.findFirst({
    where: {
      OR: [{ email }, { phone }],
    },
    select: { id: true, email: true, phone: true },
  });

  if (existing) {
    throw new AppError(
      409,
      'ACCOUNT_EXISTS',
      existing.email === email
        ? 'An account with this email already exists'
        : 'An account with this phone number already exists',
    );
  }

  const passwordHash = await bcrypt.hash(input.password, BCRYPT_ROUNDS);
  const now = new Date();
  const trialDays = await getEffectiveTrialDays(db);
  const trialExpiresAt = new Date(now.getTime() + trialDays * 24 * 60 * 60 * 1000);

  // Create user and activate automatic free trial (15 days by default) atomically only after account creation succeeds
  const result = await db.$transaction(async (tx) => {
    const user = await tx.user.create({
      data: {
        fullName: input.fullName.trim(),
        email,
        phone,
        passwordHash,
        role: UserRole.AGENT,
        accountStatus: AccountStatus.ACTIVE,
        trialConsumed: true,
      },
    });

    const entitlement = await tx.subscriptionEntitlement.create({
      data: {
        userId: user.id,
        status: EntitlementStatus.TRIAL,
        trialStartedAt: now,
        trialExpiresAt,
      },
    });

    return { user, entitlement };
  });

  await verifyAndRegisterDevice(result.user.id, input.deviceId, input.deviceName, db, {
    bypassThrottle: true,
  });

  const token = signToken(result.user.id, result.user.role);

  return {
    token,
    user: {
      id: result.user.id,
      fullName: result.user.fullName,
      email: result.user.email,
      phone: result.user.phone,
      role: result.user.role,
      accountStatus: result.user.accountStatus,
      createdAt: result.user.createdAt.toISOString(),
    },
    entitlement: computeEntitlementSummary(result.entitlement, now),
  };
}

export async function loginAgent(
  input: LoginInput,
  db: PrismaClient = defaultPrisma,
): Promise<AuthResponse> {
  const identifier = input.identifier.trim();
  const normalizedEmail = identifier.toLowerCase();

  const user = await db.user.findFirst({
    where: {
      OR: [{ email: normalizedEmail }, { phone: identifier }],
    },
  });

  if (!user) {
    throw new AppError(401, 'INVALID_CREDENTIALS', 'Invalid email/phone or password');
  }

  if (user.accountStatus === AccountStatus.SUSPENDED) {
    throw new AppError(403, 'ACCOUNT_SUSPENDED', 'Your account has been suspended');
  }

  const validPassword = await bcrypt.compare(input.password, user.passwordHash);
  if (!validPassword) {
    throw new AppError(401, 'INVALID_CREDENTIALS', 'Invalid email/phone or password');
  }

  if (user.role !== UserRole.ADMIN) {
    await verifyAndRegisterDevice(user.id, input.deviceId, input.deviceName, db, {
      bypassThrottle: true,
    });
  }

  // Never create or reset a trial on login; read existing server-side entitlement
  const entitlement = await getAndSyncEntitlement(user.id, new Date(), db);
  const token = signToken(user.id, user.role);

  return {
    token,
    user: {
      id: user.id,
      fullName: user.fullName,
      email: user.email,
      phone: user.phone,
      role: user.role,
      accountStatus: user.accountStatus,
      createdAt: user.createdAt.toISOString(),
    },
    entitlement,
  };
}

export async function getAgentProfile(
  userId: string,
  db: PrismaClient = defaultPrisma,
) {
  const user = await db.user.findUnique({
    where: { id: userId },
    select: {
      id: true,
      fullName: true,
      email: true,
      phone: true,
      role: true,
      accountStatus: true,
      createdAt: true,
    },
  });

  if (!user) {
    throw new AppError(404, 'USER_NOT_FOUND', 'User not found');
  }

  const entitlement = await getAndSyncEntitlement(user.id, new Date(), db);

  return {
    user: {
      ...user,
      createdAt: user.createdAt.toISOString(),
    },
    entitlement,
  };
}

export interface GoogleTokenPayload {
  email: string;
  fullName: string;
  sub: string;
}

export type GoogleTokenVerifier = (
  idToken: string,
  expectedClientId: string,
) => Promise<GoogleTokenPayload>;

export async function verifyGoogleIdToken(
  idToken: string,
  expectedClientId: string,
): Promise<GoogleTokenPayload> {
  const res = await fetch(
    `https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`,
  );
  if (!res.ok) {
    throw new AppError(401, 'INVALID_GOOGLE_TOKEN', 'Invalid or expired Google ID token');
  }
  const data = (await res.json()) as {
    aud?: string;
    azp?: string;
    email?: string;
    email_verified?: string | boolean;
    name?: string;
    sub?: string;
    exp?: string;
  };
  if (data.aud !== expectedClientId && data.azp !== expectedClientId) {
    throw new AppError(401, 'INVALID_GOOGLE_AUDIENCE', 'Google token client ID mismatch');
  }
  const verified = data.email_verified === true || data.email_verified === 'true';
  if (!verified || !data.email) {
    throw new AppError(401, 'GOOGLE_EMAIL_UNVERIFIED', 'Google account email is not verified');
  }
  const expSec = Number(data.exp ?? 0);
  if (Number.isFinite(expSec) && expSec > 0 && expSec * 1000 < Date.now()) {
    throw new AppError(401, 'INVALID_GOOGLE_TOKEN', 'Google ID token has expired');
  }
  const email = data.email.trim().toLowerCase();
  const fullName = data.name?.trim() || email.split('@')[0] || 'EasyMoMo Agent';
  return {
    email,
    fullName,
    sub: data.sub ?? email,
  };
}

export interface GoogleAuthInput {
  idToken: string;
  phone?: string | null;
  deviceId?: string | null;
  deviceName?: string | null;
}

export async function loginOrRegisterWithGoogle(
  input: GoogleAuthInput,
  db: PrismaClient = defaultPrisma,
  verifier: GoogleTokenVerifier = verifyGoogleIdToken,
  clientIdOverride?: string,
): Promise<AuthResponse> {
  const expectedClientId = (clientIdOverride ?? (await getEffectiveGoogleClientId(db))).trim();
  if (!expectedClientId) {
    throw new AppError(
      503,
      'GOOGLE_AUTH_NOT_CONFIGURED',
      'Google Sign-In is not configured by the administrator yet.',
    );
  }

  const googleUser = await verifier(input.idToken, expectedClientId);

  const existing = await db.user.findFirst({
    where: { email: googleUser.email },
  });

  if (existing) {
    if (existing.accountStatus === AccountStatus.SUSPENDED) {
      throw new AppError(403, 'ACCOUNT_SUSPENDED', 'Your account has been suspended');
    }
    if (existing.role !== UserRole.ADMIN) {
      await verifyAndRegisterDevice(existing.id, input.deviceId, input.deviceName, db, {
        bypassThrottle: true,
      });
    }
    const entitlement = await getAndSyncEntitlement(existing.id, new Date(), db);
    const token = signToken(existing.id, existing.role);
    return {
      token,
      user: {
        id: existing.id,
        fullName: existing.fullName,
        email: existing.email,
        phone: existing.phone,
        role: existing.role,
        accountStatus: existing.accountStatus,
        createdAt: existing.createdAt.toISOString(),
      },
      entitlement,
    };
  }

  // New agent account via Google Sign-Up -> grant automatic free trial (15 days default)
  const providedPhone = input.phone?.trim();
  let phoneToStore = `g-${crypto.randomUUID().replace(/-/g, '').slice(0, 12)}`;
  if (providedPhone) {
    const phoneInUse = await db.user.findFirst({
      where: { phone: providedPhone },
      select: { id: true },
    });
    if (!phoneInUse) {
      phoneToStore = providedPhone;
    }
  }

  const randomPasswordHash = await bcrypt.hash(crypto.randomUUID(), BCRYPT_ROUNDS);
  const now = new Date();
  const trialDays = await getEffectiveTrialDays(db);
  const trialExpiresAt = new Date(now.getTime() + trialDays * 24 * 60 * 60 * 1000);

  const result = await db.$transaction(async (tx) => {
    const user = await tx.user.create({
      data: {
        fullName: googleUser.fullName,
        email: googleUser.email,
        phone: phoneToStore,
        passwordHash: randomPasswordHash,
        role: UserRole.AGENT,
        accountStatus: AccountStatus.ACTIVE,
        trialConsumed: true,
      },
    });

    const entitlement = await tx.subscriptionEntitlement.create({
      data: {
        userId: user.id,
        status: EntitlementStatus.TRIAL,
        trialStartedAt: now,
        trialExpiresAt,
      },
    });

    return { user, entitlement };
  });

  await verifyAndRegisterDevice(result.user.id, input.deviceId, input.deviceName, db, {
    bypassThrottle: true,
  });

  const token = signToken(result.user.id, result.user.role);
  return {
    token,
    user: {
      id: result.user.id,
      fullName: result.user.fullName,
      email: result.user.email,
      phone: result.user.phone,
      role: result.user.role,
      accountStatus: result.user.accountStatus,
      createdAt: result.user.createdAt.toISOString(),
    },
    entitlement: computeEntitlementSummary(result.entitlement, now),
  };
}
