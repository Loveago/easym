import { PrismaClient } from '@prisma/client';
import request from 'supertest';
import jwt from 'jsonwebtoken';
import { describe, expect, it } from 'vitest';
import { createApp } from '../src/app';
import { env } from '../src/config/env';
import {
  deleteCustomerForAgent,
  listCustomersForAgent,
  normalizeGhanaPhone,
  syncCustomerBatch,
} from '../src/services/customerService';
import { decryptText, encryptText, hashNormalizedPhone } from '../src/utils/crypto';

interface FakeRow {
  id: string;
  userId: string;
  phoneHash: string;
  encryptedNormalizedPhone: string;
  encryptedDisplayPhone: string;
  encryptedNickname: string | null;
  firstUsedAt: Date;
  lastUsedAt: Date;
  usageCount: number;
  version: number;
  deletedAt: Date | null;
  syncedAt: Date;
  createdAt: Date;
  updatedAt: Date;
}

function createFakeCustomerDb() {
  const rows: FakeRow[] = [];
  let seq = 1;

  const db = {
    customerNumber: {
      findUnique: async ({ where }: { where: { userId_phoneHash: { userId: string; phoneHash: string } } }) => {
        const { userId, phoneHash } = where.userId_phoneHash;
        return rows.find((r) => r.userId === userId && r.phoneHash === phoneHash) ?? null;
      },
      findFirst: async ({ where }: { where: { id?: string; userId?: string } }) => {
        return (
          rows.find(
            (r) =>
              (where.id ? r.id === where.id : true) &&
              (where.userId ? r.userId === where.userId : true),
          ) ?? null
        );
      },
      create: async ({ data }: { data: Omit<FakeRow, 'id' | 'createdAt' | 'updatedAt'> }) => {
        const now = new Date();
        const row: FakeRow = {
          id: `cust-${seq++}`,
          ...data,
          createdAt: now,
          updatedAt: new Date(Math.max(data.lastUsedAt.getTime(), now.getTime())),
        };
        rows.push(row);
        return row;
      },
      update: async ({ where, data }: { where: { id: string }; data: Partial<FakeRow> }) => {
        const idx = rows.findIndex((r) => r.id === where.id);
        if (idx < 0) throw new Error('Not found');
        rows[idx] = {
          ...rows[idx],
          ...data,
          updatedAt: new Date(Date.now() + seq++),
        };
        return rows[idx];
      },
      findMany: async ({
        where,
        take,
        cursor,
      }: {
        where: { userId: string; deletedAt?: null; updatedAt?: { gt: Date } };
        take: number;
        cursor?: { id: string };
      }) => {
        let filtered = rows
          .filter((r) => r.userId === where.userId)
          .filter((r) => (where.deletedAt === null ? r.deletedAt === null : true))
          .filter((r) => (where.updatedAt?.gt ? r.updatedAt > where.updatedAt.gt : true))
          .sort((a, b) => a.updatedAt.getTime() - b.updatedAt.getTime() || a.id.localeCompare(b.id));

        if (cursor?.id) {
          const cIdx = filtered.findIndex((r) => r.id === cursor.id);
          if (cIdx >= 0) filtered = filtered.slice(cIdx + 1);
        }
        return filtered.slice(0, take);
      },
      count: async ({ where }: { where: { userId: string; deletedAt?: null | { not: null } } }) => {
        return rows.filter((r) => {
          if (r.userId !== where.userId) return false;
          if (where.deletedAt === null) return r.deletedAt === null;
          if (where.deletedAt && typeof where.deletedAt === 'object' && where.deletedAt.not === null) {
            return r.deletedAt !== null;
          }
          return true;
        }).length;
      },
      updateMany: async ({
        where,
        data,
      }: {
        where: { userId: string; deletedAt: null };
        data: Partial<FakeRow>;
      }) => {
        let count = 0;
        for (let i = 0; i < rows.length; i++) {
          if (rows[i].userId === where.userId && rows[i].deletedAt === null) {
            rows[i] = { ...rows[i], ...data, updatedAt: new Date() };
            count++;
          }
        }
        return { count };
      },
    },
  } as unknown as PrismaClient;

  return { db, rows };
}

describe('Cloud Customer Sync Service & Security', () => {
  it('normalizes Ghanaian phone formats and prevents duplicate records', async () => {
    expect(normalizeGhanaPhone('0241234567')).toBe('0241234567');
    expect(normalizeGhanaPhone('233241234567')).toBe('0241234567');
    expect(normalizeGhanaPhone('+233 24 123 4567')).toBe('0241234567');

    const { db, rows } = createFakeCustomerDb();
    await syncCustomerBatch(
      'agent-1',
      [
        {
          phoneNumber: '+233241234567',
          firstUsedAt: 1000,
          lastUsedAt: 2000,
          usageCount: 1,
          updatedAt: 2000,
        },
      ],
      db,
    );
    await syncCustomerBatch(
      'agent-1',
      [
        {
          phoneNumber: '0241234567',
          firstUsedAt: 1500,
          lastUsedAt: 3000,
          usageCount: 2,
          updatedAt: 3000,
        },
      ],
      db,
    );

    expect(rows.length).toBe(1);
    expect(rows[0].usageCount).toBe(2);
    expect(rows[0].firstUsedAt.getTime()).toBe(1000);
    expect(rows[0].lastUsedAt.getTime()).toBe(3000);
    expect(rows[0].version).toBe(2);
  });

  it('encrypts phone numbers at rest and isolates HMAC hashes per agent', () => {
    const encrypted = encryptText('0241234567');
    expect(encrypted).not.toContain('0241234567');
    expect(decryptText(encrypted)).toBe('0241234567');

    const h1 = hashNormalizedPhone('agent-1', '0241234567');
    const h2 = hashNormalizedPhone('agent-2', '0241234567');
    expect(h1).not.toBe(h2);
  });

  it('preserves deletion tombstones when an older device reconnects with stale data', async () => {
    const { db } = createFakeCustomerDb();
    const first = await syncCustomerBatch(
      'agent-1',
      [
        {
          phoneNumber: '0551112233',
          firstUsedAt: 1000,
          lastUsedAt: 2000,
          usageCount: 3,
          updatedAt: 2000,
        },
      ],
      db,
    );
    const id = first.synced[0].id;
    await deleteCustomerForAgent('agent-1', id, db);

    // Older device syncs stale non-deleted state from t=2000
    const staleSync = await syncCustomerBatch(
      'agent-1',
      [
        {
          phoneNumber: '0551112233',
          firstUsedAt: 1000,
          lastUsedAt: 2000,
          usageCount: 3,
          updatedAt: 2000,
          deletedAt: null,
        },
      ],
      db,
    );

    expect(staleSync.synced[0].deletedAt).not.toBeNull();
    const activeList = await listCustomersForAgent('agent-1', {}, db);
    expect(activeList.customers.length).toBe(0);
  });

  it('enforces strict account isolation and pagination', async () => {
    const { db } = createFakeCustomerDb();
    await syncCustomerBatch(
      'agent-A',
      [
        { phoneNumber: '0240000001', firstUsedAt: 1000, lastUsedAt: 1001, usageCount: 1, updatedAt: 1001 },
        { phoneNumber: '0240000002', firstUsedAt: 1000, lastUsedAt: 1002, usageCount: 1, updatedAt: 1002 },
        { phoneNumber: '0240000003', firstUsedAt: 1000, lastUsedAt: 1003, usageCount: 1, updatedAt: 1003 },
      ],
      db,
    );
    await syncCustomerBatch(
      'agent-B',
      [{ phoneNumber: '0240000009', firstUsedAt: 1000, lastUsedAt: 1004, usageCount: 1, updatedAt: 1004 }],
      db,
    );

    // Pagination for agent-A
    const page1 = await listCustomersForAgent('agent-A', { limit: 2 }, db);
    expect(page1.customers.length).toBe(2);
    expect(page1.hasMore).toBe(true);
    const page2 = await listCustomersForAgent('agent-A', { limit: 2, cursor: page1.nextCursor! }, db);
    expect(page2.customers.length).toBe(1);
    expect(page2.hasMore).toBe(false);

    // Agent B cannot delete Agent A's record
    await expect(deleteCustomerForAgent('agent-B', page1.customers[0].id, db)).rejects.toThrow(
      'Customer record not found',
    );
  });

  it('HTTP integration: rejects unauthenticated access to /api/customers and validates bad payloads', async () => {
    const app = createApp();
    const unauth = await request(app).get('/api/customers');
    expect(unauth.status).toBe(401);

    const token = jwt.sign({ sub: 'fake-user', role: 'AGENT' }, env.JWT_SECRET);
    const badSync = await request(app)
      .post('/api/customers/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ customers: [] });
    expect([400, 401]).toContain(badSync.status);
  });
});
