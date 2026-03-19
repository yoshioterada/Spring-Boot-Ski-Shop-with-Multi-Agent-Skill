export interface CampaignResponse {
  id: string;
  name: string;
  description: string;
  campaignType: 'SEASONAL' | 'PROMOTION' | 'LOYALTY' | 'CLEARANCE';
  startDate: string;
  endDate: string;
  active: boolean;
  maxCoupons?: number | null;
  generatedCoupons: number;
  rules?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface CouponResponse {
  id: string;
  campaignId: string;
  code: string;
  couponType: 'SINGLE_USE' | 'MULTI_USE';
  discountType: 'PERCENTAGE' | 'FIXED_AMOUNT';
  discountValue: number;
  minimumAmount: number;
  maximumDiscount?: number;
  usageLimit: number;
  usedCount: number;
  active: boolean;
  expiresAt: string;
  createdAt: string;
}

export interface UserAvailableCouponsResponse {
  userId: string;
  coupons: CouponResponse[];
  timestamp: string;
}
