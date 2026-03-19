export interface UserTierResponse {
  id: string;
  userId: string;
  tierLevel: 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM';
  tierName: string;
  totalEarned: number;
  currentBalance: number;
  pointMultiplier: number;
  benefits?: Record<string, unknown>;
  nextTier?: string | null;
  pointsToNextTier: number;
  tierUpgradedAt: string;
  createdAt: string;
}

export interface PointTransactionResponse {
  id: string;
  userId: string;
  transactionType: 'EARNED' | 'REDEEMED' | 'EXPIRED' | 'ADJUSTED' | 'TRANSFERRED';
  amount: number;
  balanceAfter: number;
  description: string;
  referenceId?: string;
  expiresAt?: string;
  createdAt: string;
}

export interface PointBalanceResponse {
  userId: string;
  currentBalance: number;
  totalEarned: number;
  expiringPoints?: number;
}

export interface RedeemPointsRequest {
  userId: string;
  points: number;
  orderId?: string;
}

export type TierLevel = UserTierResponse['tierLevel'];
