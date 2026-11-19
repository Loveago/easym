import { Router } from 'express';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth';
import { validate } from '../middleware/validate';
import {
  getAgentProfile,
  loginAgent,
  loginOrRegisterWithGoogle,
  registerAgent,
} from '../services/authService';
import { unregisterDevice } from '../services/deviceLimitService';
import { getEffectiveGoogleClientId } from '../services/settingsService';

const router = Router();

const registerSchema = z.object({
  fullName: z.string().min(2).max(120),
  email: z.string().email().max(255),
  phone: z.string().min(9).max(20),
  password: z.string().min(8).max(128),
  deviceId: z.string().max(128).nullable().optional(),
  deviceName: z.string().max(120).nullable().optional(),
});

const loginSchema = z.object({
  identifier: z.string().min(3).max(255),
  password: z.string().min(1).max(128),
  deviceId: z.string().max(128).nullable().optional(),
  deviceName: z.string().max(120).nullable().optional(),
});

const googleAuthSchema = z.object({
  idToken: z.string().min(20),
  phone: z.string().max(20).nullable().optional(),
  deviceId: z.string().max(128).nullable().optional(),
  deviceName: z.string().max(120).nullable().optional(),
});

router.get('/google/config', async (_req, res, next) => {
  try {
    const clientId = await getEffectiveGoogleClientId();
    res.status(200).json({
      enabled: Boolean(clientId),
      clientId,
    });
  } catch (err) {
    next(err);
  }
});

router.post('/google', validate(googleAuthSchema), async (req, res, next) => {
  try {
    const headerDeviceId = req.headers['x-device-id'];
    const headerDeviceName = req.headers['x-device-name'];
    const result = await loginOrRegisterWithGoogle({
      ...req.body,
      deviceId:
        req.body.deviceId || (Array.isArray(headerDeviceId) ? headerDeviceId[0] : headerDeviceId),
      deviceName:
        req.body.deviceName ||
        (Array.isArray(headerDeviceName) ? headerDeviceName[0] : headerDeviceName),
    });
    res.status(200).json(result);
  } catch (err) {
    next(err);
  }
});

router.post('/register', validate(registerSchema), async (req, res, next) => {
  try {
    const headerDeviceId = req.headers['x-device-id'];
    const headerDeviceName = req.headers['x-device-name'];
    const result = await registerAgent({
      ...req.body,
      deviceId:
        req.body.deviceId || (Array.isArray(headerDeviceId) ? headerDeviceId[0] : headerDeviceId),
      deviceName:
        req.body.deviceName ||
        (Array.isArray(headerDeviceName) ? headerDeviceName[0] : headerDeviceName),
    });
    res.status(201).json(result);
  } catch (err) {
    next(err);
  }
});

router.post('/login', validate(loginSchema), async (req, res, next) => {
  try {
    const headerDeviceId = req.headers['x-device-id'];
    const headerDeviceName = req.headers['x-device-name'];
    const result = await loginAgent({
      ...req.body,
      deviceId:
        req.body.deviceId || (Array.isArray(headerDeviceId) ? headerDeviceId[0] : headerDeviceId),
      deviceName:
        req.body.deviceName ||
        (Array.isArray(headerDeviceName) ? headerDeviceName[0] : headerDeviceName),
    });
    res.status(200).json(result);
  } catch (err) {
    next(err);
  }
});

router.get('/me', requireAuth, async (req, res, next) => {
  try {
    const profile = await getAgentProfile(req.user!.id);
    res.status(200).json(profile);
  } catch (err) {
    next(err);
  }
});

router.post('/logout', requireAuth, async (req, res, next) => {
  try {
    const headerDeviceId = req.headers['x-device-id'];
    const deviceId =
      req.body?.deviceId || (Array.isArray(headerDeviceId) ? headerDeviceId[0] : headerDeviceId);
    await unregisterDevice(req.user!.id, deviceId);
    res.status(200).json({ message: 'Logged out successfully' });
  } catch (err) {
    next(err);
  }
});

export default router;
