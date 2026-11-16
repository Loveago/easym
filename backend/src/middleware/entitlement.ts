import { NextFunction, Request, Response } from 'express';
import { AppError } from './errorHandler';
import { EntitlementSummary, getAndSyncEntitlement } from '../services/trialService';

declare global {
  namespace Express {
    interface Request {
      entitlement?: EntitlementSummary;
    }
  }
}

export async function requireActiveEntitlement(
  req: Request,
  _res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    if (!req.user) {
      throw new AppError(401, 'UNAUTHORIZED', 'Authentication required');
    }

    const entitlement = await getAndSyncEntitlement(req.user.id);
    req.entitlement = entitlement;

    if (!entitlement.hasAccess) {
      throw new AppError(
        402,
        'SUBSCRIPTION_REQUIRED',
        'Your 7-day free trial or subscription has expired. Please renew to continue.',
        { entitlement },
      );
    }

    next();
  } catch (err) {
    next(err);
  }
}
