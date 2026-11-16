import crypto from 'crypto';
import { env } from '../config/env';

function getEncryptionKey(): Buffer {
  // Derive a 32-byte AES-256 key from JWT_SECRET (or dedicated CUSTOMER_ENCRYPTION_KEY if provided)
  const rawSecret = process.env.CUSTOMER_ENCRYPTION_KEY || env.JWT_SECRET;
  return crypto.createHash('sha256').update(rawSecret).digest();
}

export function hashNormalizedPhone(userId: string, normalizedPhone: string): string {
  const key = getEncryptionKey();
  return crypto.createHmac('sha256', key).update(`${userId}:${normalizedPhone}`).digest('hex');
}

export function encryptText(plain: string): string {
  const key = getEncryptionKey();
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const encrypted = Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return `${iv.toString('base64')}:${tag.toString('base64')}:${encrypted.toString('base64')}`;
}

export function decryptText(payload: string): string {
  const parts = payload.split(':');
  if (parts.length !== 3) return payload;
  const [ivB64, tagB64, dataB64] = parts;
  const key = getEncryptionKey();
  const iv = Buffer.from(ivB64, 'base64');
  const tag = Buffer.from(tagB64, 'base64');
  const data = Buffer.from(dataB64, 'base64');
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(data), decipher.final()]).toString('utf8');
}
