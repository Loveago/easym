import { Router } from 'express';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth';
import { validate } from '../middleware/validate';
import { getAgentProfile, loginAgent, registerAgent } from '../services/authService';

const router = Router();

const registerSchema = z.object({
  fullName: z.string().min(2).max(120),
  email: z.string().email().max(255),
  phone: z.string().min(9).max(20),
  password: z.string().min(8).max(128),
});

const loginSchema = z.object({
  identifier: z.string().min(3).max(255),
  password: z.string().min(1).max(128),
});

router.post('/register', validate(registerSchema), async (req, res, next) => {
  try {
    const result = await registerAgent(req.body);
    res.status(201).json(result);
  } catch (err) {
    next(err);
  }
});

router.post('/login', validate(loginSchema), async (req, res, next) => {
  try {
    const result = await loginAgent(req.body);
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

router.post('/logout', requireAuth, (_req, res) => {
  res.status(200).json({ message: 'Logged out successfully' });
});

export default router;
