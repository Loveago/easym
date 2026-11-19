import { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { AccountStatus, UserRole } from '@prisma/client';
import { env } from '../config/env';
import { prisma } from '../config/prisma';
import { verifyAndRegisterDevice } from '../services/deviceLimitService';
import { AppError } from './errorHandler';

export interface AuthenticatedUser {
  id: string;
  email: string;
  phone: string;
  fullName: string;
  role: UserRole;
  accountStatus: AccountStatus;
}

declare global {
  namespace Express {
    interface Request {
      user?: AuthenticatedUser;
    }
  }
}

interface JwtPayload {
  sub: string;
  role: UserRole;
}

export async function requireAuth(
  req: Request,
  _res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      throw new AppError(401, 'UNAUTHORIZED', 'Authentication token is required');
    }

    const token = authHeader.slice(7).trim();
    if (!token) {
      throw new AppError(401, 'UNAUTHORIZED', 'Authentication token is required');
    }

    let payload: JwtPayload;
    try {
      payload = jwt.verify(token, env.JWT_SECRET) as JwtPayload;
    } catch {
      throw new AppError(401, 'INVALID_TOKEN', 'Invalid or expired authentication token');
    }

    const user = await prisma.user.findUnique({
      where: { id: payload.sub },
      select: {
        id: true,
        email: true,
        phone: true,
        fullName: true,
        role: true,
        accountStatus: true,
      },
    });

    if (!user) {
      throw new AppError(401, 'UNAUTHORIZED', 'User account not found');
    }

    if (user.accountStatus === AccountStatus.SUSPENDED) {
      throw new AppError(403, 'ACCOUNT_SUSPENDED', 'Your account has been suspended');
    }

    const headerDeviceId = req.headers['x-device-id'];
    const deviceId = Array.isArray(headerDeviceId) ? headerDeviceId[0] : headerDeviceId;
    const headerDeviceName = req.headers['x-device-name'];
    const deviceName = Array.isArray(headerDeviceName) ? headerDeviceName[0] : headerDeviceName;
    if (deviceId && user.role !== UserRole.ADMIN && req.path !== '/logout') {
      await verifyAndRegisterDevice(user.id, deviceId, deviceName, prisma);
    }

    req.user = user;
    next();
  } catch (err) {
    next(err);
  }
}

export function requireAdmin(req: Request, _res: Response, next: NextFunction): void {
  if (!req.user) {
    next(new AppError(401, 'UNAUTHORIZED', 'Authentication required'));
    return;
  }
  if (req.user.role !== UserRole.ADMIN) {
    next(new AppError(403, 'FORBIDDEN', 'Administrator privileges required'));
    return;
  }
  next();
}
