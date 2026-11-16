import { describe, expect, it, vi } from 'vitest';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { AccountStatus, EntitlementStatus, PrismaClient, UserRole } from '@prisma/client';
import { createApp } from '../src/app';
import { env } from '../src/config/env';
import { prisma } from '../src/config/prisma';
import { loginAgent, registerAgent } from '../src/services/authService';

describe('Authentication & Entitlement Middleware', () => {
  it('registers a new agent and activates 7-day TRIAL atomically', async () => {
    const createdUser = {
      id: 'user-123',
      fullName: 'Kwame Mensah',
      email: 'kwame@example.com',
      phone: '0241234567',
      passwordHash: 'hashed',
      role: UserRole.AGENT,
      accountStatus: AccountStatus.ACTIVE,
      trialConsumed: true,
      createdAt: new Date(),
    };

    const mockDb = {
      user: {
        findFirst: vi.fn().mockResolvedValue(null),
      },
      $transaction: vi.fn(async (cb: (tx: unknown) => Promise<unknown>) => {
        const tx = {
          user: { create: vi.fn().mockResolvedValue(createdUser) },
          subscriptionEntitlement: {
            create: vi.fn().mockImplementation(({ data }) =>
              Promise.resolve({
                id: 'ent-1',
                ...data,
                paidUntil: null,
                currentPlanId: null,
                recurringAuthorized: false,
              }),
            ),
          },
        };
        return cb(tx);
      }),
    } as unknown as PrismaClient;

    const result = await registerAgent(
      {
        fullName: 'Kwame Mensah',
        email: 'Kwame@Example.com',
        phone: '0241234567',
        password: 'StrongPassword123!',
      },
      mockDb,
    );

    expect(result.token).toBeTruthy();
    expect(result.user.email).toBe('kwame@example.com');
    expect(result.entitlement.status).toBe(EntitlementStatus.TRIAL);
    expect(result.entitlement.hasAccess).toBe(true);
    expect(result.entitlement.remainingDays).toBe(7);
  });

  it('rejects duplicate email or phone on registration', async () => {
    const mockDb = {
      user: {
        findFirst: vi.fn().mockResolvedValue({
          id: 'existing-1',
          email: 'kwame@example.com',
          phone: '0241234567',
        }),
      },
    } as unknown as PrismaClient;

    await expect(
      registerAgent(
        {
          fullName: 'Kwame',
          email: 'kwame@example.com',
          phone: '0241234567',
          password: 'Password123!',
        },
        mockDb,
      ),
    ).rejects.toMatchObject({ statusCode: 409, code: 'ACCOUNT_EXISTS' });
  });

  it('logs in without resetting an expired trial', async () => {
    const passwordHash = await bcrypt.hash('MySecret123!', 4);
    const pastStart = new Date('2026-10-01T00:00:00.000Z');
    const pastExpiry = new Date('2026-10-08T00:00:00.000Z');
    const updateMock = vi.fn().mockResolvedValue({});

    const mockDb = {
      user: {
        findFirst: vi.fn().mockResolvedValue({
          id: 'user-1',
          fullName: 'Ama Boateng',
          email: 'ama@example.com',
          phone: '0551234567',
          passwordHash,
          role: UserRole.AGENT,
          accountStatus: AccountStatus.ACTIVE,
          createdAt: pastStart,
        }),
      },
      subscriptionEntitlement: {
        findUnique: vi.fn().mockResolvedValue({
          id: 'ent-1',
          userId: 'user-1',
          status: EntitlementStatus.TRIAL,
          trialStartedAt: pastStart,
          trialExpiresAt: pastExpiry,
          paidUntil: null,
          currentPlanId: null,
          recurringAuthorized: false,
        }),
        update: updateMock,
      },
    } as unknown as PrismaClient;

    const res = await loginAgent(
      { identifier: 'ama@example.com', password: 'MySecret123!' },
      mockDb,
    );

    expect(res.entitlement.status).toBe(EntitlementStatus.EXPIRED);
    expect(res.entitlement.hasAccess).toBe(false);
    expect(updateMock).toHaveBeenCalled();
  });

  it('blocks suspended accounts on login', async () => {
    const passwordHash = await bcrypt.hash('MySecret123!', 4);
    const mockDb = {
      user: {
        findFirst: vi.fn().mockResolvedValue({
          id: 'user-2',
          fullName: 'Suspended Agent',
          email: 'susp@example.com',
          phone: '0201112222',
          passwordHash,
          role: UserRole.AGENT,
          accountStatus: AccountStatus.SUSPENDED,
          createdAt: new Date(),
        }),
      },
    } as unknown as PrismaClient;

    await expect(
      loginAgent({ identifier: 'susp@example.com', password: 'MySecret123!' }, mockDb),
    ).rejects.toMatchObject({ statusCode: 403, code: 'ACCOUNT_SUSPENDED' });
  });

  it('HTTP integration: healthcheck returns 200, protected route rejects missing token, expired trial returns 402 on sync', async () => {
    const app = createApp();

    const health = await request(app).get('/api/health');
    expect(health.status).toBe(200);
    expect(health.body.status).toBe('ok');

    const unauth = await request(app).get('/api/auth/me');
    expect(unauth.status).toBe(401);

    const token = jwt.sign({ sub: 'user-expired', role: UserRole.AGENT }, env.JWT_SECRET);
    const userSpy = vi.spyOn(prisma.user, 'findUnique').mockResolvedValue({
      id: 'user-expired',
      email: 'exp@example.com',
      phone: '0240000000',
      fullName: 'Expired User',
      role: UserRole.AGENT,
      accountStatus: AccountStatus.ACTIVE,
    } as never);
    const entSpy = vi.spyOn(prisma.subscriptionEntitlement, 'findUnique').mockResolvedValue({
      id: 'ent-exp',
      userId: 'user-expired',
      status: EntitlementStatus.EXPIRED,
      trialStartedAt: new Date('2026-10-01T00:00:00Z'),
      trialExpiresAt: new Date('2026-10-08T00:00:00Z'),
      paidUntil: null,
      currentPlanId: null,
      recurringAuthorized: false,
    } as never);

    const syncRes = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ transactions: [] });

    expect(syncRes.status).toBe(402);
    expect(syncRes.body.error.code).toBe('SUBSCRIPTION_REQUIRED');

    userSpy.mockRestore();
    entSpy.mockRestore();
  });
});
