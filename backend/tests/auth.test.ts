import { describe, expect, it, vi } from 'vitest';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { AccountStatus, EntitlementStatus, PrismaClient, UserRole } from '@prisma/client';
import { createApp } from '../src/app';
import { env } from '../src/config/env';
import { prisma } from '../src/config/prisma';
import {
  loginAgent,
  loginOrRegisterWithGoogle,
  registerAgent,
} from '../src/services/authService';
import {
  clearDeviceLimitTestState,
  unregisterDevice,
  verifyAndRegisterDevice,
} from '../src/services/deviceLimitService';
import { clearTrialDeviceTestState } from '../src/services/trialDeviceService';

describe('Authentication & Entitlement Middleware', () => {
  it('registers a new agent and activates 15-day TRIAL atomically', async () => {
    clearTrialDeviceTestState();
    clearDeviceLimitTestState();
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
        deviceId: 'dev-register-1',
      },
      mockDb,
    );

    expect(result.token).toBeTruthy();
    expect(result.user.email).toBe('kwame@example.com');
    expect(result.entitlement.status).toBe(EntitlementStatus.TRIAL);
    expect(result.entitlement.hasAccess).toBe(true);
    expect(result.entitlement.remainingDays).toBe(15);
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
          deviceId: 'dev-dup-1',
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
      createdAt: new Date('2026-10-01T00:00:00Z'),
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

    const auth = { Authorization: `Bearer ${token}` };
    const syncRes = await request(app).post('/api/transactions/sync').set(auth).send({ transactions: [] });
    expect(syncRes.status).toBe(402);
    expect(syncRes.body.error.code).toBe('SUBSCRIPTION_REQUIRED');

    for (const path of [
      '/api/transactions',
      '/api/customers',
      '/api/reports/summary',
      '/api/config/ussd-flows',
      '/api/config/bundles',
    ]) {
      const r = await request(app).get(path).set(auth);
      expect(r.status, path).toBe(402);
    }

    const me = await request(app).get('/api/auth/me').set(auth);
    expect(me.status).toBe(200);
    const status = await request(app).get('/api/subscriptions/status').set(auth);
    expect(status.status).toBe(200);

    userSpy.mockRestore();
    entSpy.mockRestore();
  });

  it('locks active devices per account to 2 and releases a slot on logout', async () => {
    clearDeviceLimitTestState();

    // Device 1 and Device 2 succeed
    const d1 = await verifyAndRegisterDevice('user-dev-test', 'device-1', 'Samsung A14');
    expect(d1.activeDevices).toBe(1);
    const d2 = await verifyAndRegisterDevice('user-dev-test', 'device-2', 'Tecno Spark');
    expect(d2.activeDevices).toBe(2);

    // Re-verifying existing Device 1 succeeds
    const d1Again = await verifyAndRegisterDevice('user-dev-test', 'device-1', 'Samsung A14', undefined, {
      bypassThrottle: true,
    });
    expect(d1Again.activeDevices).toBe(2);

    // Device 3 is blocked with 403 DEVICE_LIMIT_REACHED
    await expect(
      verifyAndRegisterDevice('user-dev-test', 'device-3', 'Infinix Hot', undefined, {
        bypassThrottle: true,
      }),
    ).rejects.toMatchObject({
      statusCode: 403,
      code: 'DEVICE_LIMIT_REACHED',
    });

    // Signing out Device 1 frees a slot so Device 3 can register
    await unregisterDevice('user-dev-test', 'device-1');
    const d3AfterLogout = await verifyAndRegisterDevice('user-dev-test', 'device-3', 'Infinix Hot', undefined, {
      bypassThrottle: true,
    });
    expect(d3AfterLogout.activeDevices).toBe(2);
  });

  it('registers new agent via Google Sign-Up with 15-day trial and rejects when unconfigured', async () => {
    clearDeviceLimitTestState();
    clearTrialDeviceTestState();

    await expect(
      loginOrRegisterWithGoogle(
        { idToken: 'fake-token-1234567890123456', deviceId: 'dev-g0' },
        undefined,
        undefined,
        '',
      ),
    ).rejects.toMatchObject({ statusCode: 503, code: 'GOOGLE_AUTH_NOT_CONFIGURED' });

    const createdUser = {
      id: 'google-user-1',
      fullName: 'Kofi Mensah',
      email: 'kofi@gmail.com',
      phone: 'g-1234567890ab',
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
                id: 'ent-g1',
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

    const fakeVerifier = vi.fn().mockResolvedValue({
      email: 'kofi@gmail.com',
      fullName: 'Kofi Mensah',
      sub: 'sub-google-1',
    });

    const res = await loginOrRegisterWithGoogle(
      { idToken: 'valid-token-1234567890123456', deviceId: 'dev-g1', deviceName: 'Pixel 8' },
      mockDb,
      fakeVerifier,
      '12345.apps.googleusercontent.com',
    );

    expect(res.token).toBeTruthy();
    expect(res.user.email).toBe('kofi@gmail.com');
    expect(res.entitlement.status).toBe(EntitlementStatus.TRIAL);
    expect(res.entitlement.remainingDays).toBe(15);
  });

  it('enforces 1 free trial per deviceId and requires deviceId on register and google signup', async () => {
    clearDeviceLimitTestState();
    clearTrialDeviceTestState();
    const app = createApp();

    const missingRegRes = await request(app).post('/api/auth/register').send({
      fullName: 'Test Agent',
      email: 'test-no-dev@example.com',
      phone: '0244000001',
      password: 'Password123!',
    });
    expect(missingRegRes.status).toBe(400);
    expect(missingRegRes.body.error.code).toBe('DEVICE_ID_REQUIRED');

    const missingGoogleRes = await request(app).post('/api/auth/google').send({
      idToken: 'valid-token-1234567890123456',
    });
    expect(missingGoogleRes.status).toBe(400);
    expect(missingGoogleRes.body.error.code).toBe('DEVICE_ID_REQUIRED');

    const makeMockDb = (id: string, email: string, phone: string) =>
      ({
        user: { findFirst: vi.fn().mockResolvedValue(null) },
        $transaction: vi.fn(async (cb: (tx: unknown) => Promise<unknown>) => {
          const tx = {
            user: {
              create: vi.fn().mockResolvedValue({
                id,
                fullName: 'Agent',
                email,
                phone,
                passwordHash: 'hashed',
                role: UserRole.AGENT,
                accountStatus: AccountStatus.ACTIVE,
                trialConsumed: true,
                createdAt: new Date(),
              }),
            },
            subscriptionEntitlement: {
              create: vi.fn().mockImplementation(({ data }) =>
                Promise.resolve({
                  id: `ent-${id}`,
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
      }) as unknown as PrismaClient;

    // 1st signup on device gets 15-day TRIAL
    const first = await registerAgent(
      {
        fullName: 'First Agent',
        email: 'first@example.com',
        phone: '0244111111',
        password: 'Password123!',
        deviceId: 'android-id-shared-1',
      },
      makeMockDb('u-1', 'first@example.com', '0244111111'),
    );
    expect(first.entitlement.status).toBe(EntitlementStatus.TRIAL);
    expect(first.entitlement.hasAccess).toBe(true);
    expect(first.trialNotice).toBeUndefined();

    // 2nd signup (Google) on same device gets EXPIRED and notice
    const fakeVerifier = vi.fn().mockResolvedValue({
      email: 'second@gmail.com',
      fullName: 'Second Agent',
      sub: 'sub-2',
    });
    const second = await loginOrRegisterWithGoogle(
      {
        idToken: 'valid-token-1234567890123456',
        deviceId: 'android-id-shared-1',
      },
      makeMockDb('u-2', 'second@gmail.com', 'g-222'),
      fakeVerifier,
      '12345.apps.googleusercontent.com',
    );
    expect(second.entitlement.status).toBe(EntitlementStatus.EXPIRED);
    expect(second.entitlement.hasAccess).toBe(false);
    expect(second.trialNotice).toBe('Free trial already used on this device. Subscribe to continue.');
  });
});
