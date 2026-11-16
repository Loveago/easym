import { AccountStatus, BillingInterval, EntitlementStatus, NetworkType, Prisma, PrismaClient } from '@prisma/client';
import { prisma as defaultPrisma } from '../config/prisma';
import { AppError } from '../middleware/errorHandler';

const MS_PER_DAY = 24 * 60 * 60 * 1000;

export async function getActiveUssdFlows(
  network?: NetworkType,
  db: PrismaClient = defaultPrisma,
) {
  const flows = await db.ussdFlowProfile.findMany({
    where: {
      enabled: true,
      ...(network ? { network } : {}),
    },
    orderBy: [{ network: 'asc' }, { transactionType: 'asc' }, { version: 'desc' }],
  });

  return flows.map((f) => ({
    id: f.id,
    network: f.network,
    transactionType: f.transactionType,
    version: f.version,
    configuration: f.configuration,
    enabled: f.enabled,
    lastVerifiedAt: f.lastVerifiedAt?.toISOString() ?? null,
    updatedAt: f.updatedAt.toISOString(),
  }));
}

export async function getNetworkProfiles(db: PrismaClient = defaultPrisma) {
  const profiles = await db.networkProfile.findMany({
    orderBy: { network: 'asc' },
  });
  return profiles.map((p) => ({
    id: p.id,
    network: p.network,
    configuration: p.configuration,
    version: p.version,
    updatedAt: p.updatedAt.toISOString(),
  }));
}

export async function getActiveDataBundles(
  network?: NetworkType,
  db: PrismaClient = defaultPrisma,
) {
  const bundles = await db.dataBundle.findMany({
    where: {
      active: true,
      ...(network ? { network } : {}),
    },
    orderBy: [{ network: 'asc' }, { price: 'asc' }],
  });

  return bundles.map((b) => ({
    id: b.id,
    network: b.network,
    name: b.name,
    allowance: b.allowance,
    price: Number(b.price),
    validity: b.validity,
    providerOption: b.providerOption,
    active: b.active,
    lastVerifiedAt: b.lastVerifiedAt?.toISOString() ?? null,
  }));
}

export async function publishUssdFlow(
  input: {
    network: NetworkType;
    transactionType: string;
    configuration: Record<string, unknown>;
    enabled: boolean;
    markVerified: boolean;
  },
  db: PrismaClient = defaultPrisma,
) {
  return db.$transaction(async (tx) => {
    const latest = await tx.ussdFlowProfile.findFirst({
      where: {
        network: input.network,
        transactionType: input.transactionType,
      },
      orderBy: { version: 'desc' },
      select: { version: true },
    });

    const nextVersion = (latest?.version ?? 0) + 1;

    if (input.enabled) {
      await tx.ussdFlowProfile.updateMany({
        where: {
          network: input.network,
          transactionType: input.transactionType,
          enabled: true,
        },
        data: { enabled: false },
      });
    }

    return tx.ussdFlowProfile.create({
      data: {
        network: input.network,
        transactionType: input.transactionType,
        version: nextVersion,
        configuration: input.configuration as Prisma.InputJsonValue,
        enabled: input.enabled,
        lastVerifiedAt: input.markVerified ? new Date() : null,
      },
    });
  });
}

export async function rollbackUssdFlow(
  network: NetworkType,
  transactionType: string,
  targetVersion: number,
  db: PrismaClient = defaultPrisma,
) {
  const target = await db.ussdFlowProfile.findUnique({
    where: {
      network_transactionType_version: {
        network,
        transactionType,
        version: targetVersion,
      },
    },
  });

  if (!target) {
    throw new AppError(404, 'FLOW_VERSION_NOT_FOUND', 'Target USSD flow version not found');
  }

  return db.$transaction(async (tx) => {
    await tx.ussdFlowProfile.updateMany({
      where: { network, transactionType, enabled: true },
      data: { enabled: false },
    });
    return tx.ussdFlowProfile.update({
      where: { id: target.id },
      data: { enabled: true },
    });
  });
}

export async function upsertNetworkProfile(
  network: NetworkType,
  configuration: Record<string, unknown>,
  db: PrismaClient = defaultPrisma,
) {
  const existing = await db.networkProfile.findUnique({ where: { network } });
  if (!existing) {
    return db.networkProfile.create({
      data: {
        network,
        configuration: configuration as Prisma.InputJsonValue,
        version: 1,
      },
    });
  }
  return db.networkProfile.update({
    where: { network },
    data: {
      configuration: configuration as Prisma.InputJsonValue,
      version: existing.version + 1,
    },
  });
}

export async function createDataBundle(
  input: {
    network: NetworkType;
    name: string;
    allowance: string;
    price: number;
    validity: string;
    providerOption: string;
    active: boolean;
  },
  db: PrismaClient = defaultPrisma,
) {
  return db.dataBundle.create({
    data: {
      network: input.network,
      name: input.name,
      allowance: input.allowance,
      price: new Prisma.Decimal(input.price.toFixed(2)),
      validity: input.validity,
      providerOption: input.providerOption,
      active: input.active,
      lastVerifiedAt: new Date(),
    },
  });
}

export async function updateUserAccountStatus(
  userId: string,
  accountStatus: AccountStatus,
  db: PrismaClient = defaultPrisma,
) {
  return db.user.update({
    where: { id: userId },
    data: { accountStatus },
    select: {
      id: true,
      email: true,
      fullName: true,
      accountStatus: true,
    },
  });
}

export async function grantTrialExtension(
  userId: string,
  extraDays: number,
  db: PrismaClient = defaultPrisma,
) {
  const entitlement = await db.subscriptionEntitlement.findUnique({ where: { userId } });
  if (!entitlement) {
    throw new AppError(404, 'ENTITLEMENT_NOT_FOUND', 'User entitlement not found');
  }

  const now = new Date();
  const base =
    entitlement.trialExpiresAt && entitlement.trialExpiresAt.getTime() > now.getTime()
      ? entitlement.trialExpiresAt
      : now;
  const newExpires = new Date(base.getTime() + extraDays * MS_PER_DAY);

  return db.subscriptionEntitlement.update({
    where: { userId },
    data: {
      status: EntitlementStatus.TRIAL,
      trialStartedAt: entitlement.trialStartedAt ?? now,
      trialExpiresAt: newExpires,
    },
  });
}

export async function upsertSubscriptionPlan(
  input: {
    code: string;
    name: string;
    price: number;
    currency: string;
    billingInterval: BillingInterval;
    durationDays: number;
    active: boolean;
  },
  db: PrismaClient = defaultPrisma,
) {
  return db.subscriptionPlan.upsert({
    where: { code: input.code },
    create: {
      code: input.code,
      name: input.name,
      price: new Prisma.Decimal(input.price.toFixed(2)),
      currency: input.currency,
      billingInterval: input.billingInterval,
      durationDays: input.durationDays,
      active: input.active,
    },
    update: {
      name: input.name,
      price: new Prisma.Decimal(input.price.toFixed(2)),
      currency: input.currency,
      billingInterval: input.billingInterval,
      durationDays: input.durationDays,
      active: input.active,
    },
  });
}
