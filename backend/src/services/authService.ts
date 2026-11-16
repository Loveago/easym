import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { AccountStatus, EntitlementStatus, PrismaClient, UserRole } from '@prisma/client';
import { env } from '../config/env';
import { prisma as defaultPrisma } from '../config/prisma';
import { AppError } from '../middleware/errorHandler';
import {
  EntitlementSummary,
  TRIAL_DURATION_DAYS,
  computeEntitlementSummary,
  getAndSyncEntitlement,
} from './trialService';

const BCRYPT_ROUNDS = 12;

export interface RegisterInput {
  fullName: string;
  email: string;
  phone: string;
  password: string;
}

export interface LoginInput {
  identifier: string; // email or phone
  password: string;
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
  const trialExpiresAt = new Date(now.getTime() + TRIAL_DURATION_DAYS * 24 * 60 * 60 * 1000);

  // Create user and activate 7-day free trial atomically only after account creation succeeds
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
