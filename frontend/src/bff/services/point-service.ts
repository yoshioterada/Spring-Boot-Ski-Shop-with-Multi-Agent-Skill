import { apiClient } from '@/lib/api-client';

import type {
  PaginatedResponse,
  PaginationParams,
  PointBalanceResponse,
  PointTransactionResponse,
  RedeemPointsRequest,
  UserTierResponse,
} from '@/types/api';

export const pointService = {
  async getBalance(userId: string) {
    const res = await apiClient.get<PointBalanceResponse>(`/api/v1/points/${userId}/balance`);
    return res.data;
  },

  async getTier(userId: string) {
    const res = await apiClient.get<UserTierResponse>(`/api/v1/points/${userId}/tier`);
    return res.data;
  },

  async getTransactions(userId: string, params?: PaginationParams) {
    const res = await apiClient.get<PaginatedResponse<PointTransactionResponse>>(
      `/api/v1/points/${userId}/transactions`,
      { params: params as Record<string, string | number | boolean | undefined> },
    );
    return res.data;
  },

  async redeem(data: RedeemPointsRequest) {
    const res = await apiClient.post<void>('/api/v1/points/redeem', data);
    return res.data;
  },
};
