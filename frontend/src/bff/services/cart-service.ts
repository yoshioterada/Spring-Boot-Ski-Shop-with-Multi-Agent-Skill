import type { CartItemRequest, CartResponse } from '@/types/api';

interface CartPageResponse {
  cart: CartResponse;
  points: unknown;
  coupons: unknown;
}

export const cartService = {
  async get(_userId: string) {
    const res = await fetch('/api/cart');
    if (!res.ok) {
      throw new Error(`Failed to fetch cart: ${res.status}`);
    }
    const data: CartPageResponse = await res.json();
    return data.cart;
  },

  async addItem(_userId: string, item: CartItemRequest) {
    const res = await fetch('/api/cart/items', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(item),
    });
    if (!res.ok) {
      throw new Error(`Failed to add item: ${res.status}`);
    }
    return res.json() as Promise<CartResponse>;
  },

  async updateItem(_userId: string, itemId: string, quantity: number) {
    const res = await fetch(`/api/cart/items/${itemId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ quantity }),
    });
    if (!res.ok) {
      throw new Error(`Failed to update item: ${res.status}`);
    }
    return res.json() as Promise<CartResponse>;
  },

  async removeItem(_userId: string, itemId: string) {
    const res = await fetch(`/api/cart/items/${itemId}`, {
      method: 'DELETE',
    });
    if (!res.ok) {
      throw new Error(`Failed to remove item: ${res.status}`);
    }
    return res.json() as Promise<CartResponse>;
  },
};
