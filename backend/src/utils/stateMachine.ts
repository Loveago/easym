import { TransactionStatus } from '@prisma/client';

const ALLOWED_TRANSITIONS: Record<TransactionStatus, TransactionStatus[]> = {
  DRAFT: ['PROCESSING', 'CANCELLED'],
  PROCESSING: [
    'AWAITING_CUSTOMER',
    'AWAITING_VERIFICATION',
    'SUCCESSFUL',
    'FAILED',
    'CANCELLED',
    'UNKNOWN',
  ],
  AWAITING_CUSTOMER: ['AWAITING_VERIFICATION', 'SUCCESSFUL', 'FAILED', 'CANCELLED', 'UNKNOWN'],
  AWAITING_VERIFICATION: ['SUCCESSFUL', 'FAILED', 'CANCELLED', 'UNKNOWN'],
  SUCCESSFUL: [],
  FAILED: [],
  CANCELLED: [],
  // Section 17: An unknown transaction must not automatically become successful.
  UNKNOWN: ['FAILED', 'CANCELLED'],
};

export function isValidTransactionTransition(
  from: TransactionStatus,
  to: TransactionStatus,
): boolean {
  if (from === to) return true;
  return ALLOWED_TRANSITIONS[from]?.includes(to) ?? false;
}

export function getAllowedNextStatuses(from: TransactionStatus): TransactionStatus[] {
  return ALLOWED_TRANSITIONS[from] ?? [];
}
