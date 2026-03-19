import { apiClient } from '@/lib/api-client';

import type { CartItemRequest, CartResponse } from '@/types/api';

export const cartService = {
  async get(userId: string) {
    const res = await apiClient.get<CartResponse>(`/api/v1/cart/${userId}`);
    return res.data;
  },

  async addItem(userId: string, item: CartItemRequest) {
    const res = await apiClient.post<CartResponse>(`/api/v1/cart/${userId}/items`, item);
    return res.data;
  },

  async updateItem(userId: string, itemId: string, quantity: number) {
    const res = await apiClient.put<CartResponse>(`/api/v1/cart/${userId}/items/${itemId}`, {
      quantity,
    });
    return res.data;
  },

  async removeItem(userId: string, itemId: string) {
    const res = await apiClient.delete<CartResponse>(`/api/v1/cart/${userId}/items/${itemId}`);
    return res.data;
  },
};
