import { apiClient } from '@/lib/api-client';

import type {
  CreateOrderRequest,
  OrderResponse,
  PaginatedResponse,
  PaginationParams,
} from '@/types/api';

export const orderService = {
  async listByCustomer(customerId: string, params?: PaginationParams) {
    const res = await apiClient.get<PaginatedResponse<OrderResponse>>(
      `/api/v1/orders/customer/${customerId}`,
      { params: params as Record<string, string | number | boolean | undefined> },
    );
    return res.data;
  },

  async getById(id: string) {
    const res = await apiClient.get<OrderResponse>(`/api/v1/orders/${id}`);
    return res.data;
  },

  async create(data: CreateOrderRequest) {
    const res = await apiClient.post<OrderResponse>('/api/v1/orders', data);
    return res.data;
  },

  async cancel(id: string) {
    const res = await apiClient.put<OrderResponse>(`/api/v1/orders/${id}/cancel`);
    return res.data;
  },
};
