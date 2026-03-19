'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';

import { cartService } from '@/bff/services/cart-service';
import { useAuthStore } from '@/stores/auth-store';

import type { CartItemRequest, CartResponse } from '@/types/api';

const CART_QUERY_KEY = ['cart'];

export function useCart() {
  const queryClient = useQueryClient();
  const { userId, isAuthenticated } = useAuthStore();

  const {
    data: cart,
    isLoading,
    error,
  } = useQuery({
    queryKey: CART_QUERY_KEY,
    queryFn: () => cartService.get(userId!),
    enabled: isAuthenticated && !!userId,
    staleTime: 0, // Always fetch fresh cart data
    gcTime: 5 * 60 * 1000,
  });

  const addItemMutation = useMutation({
    mutationFn: (item: CartItemRequest) => cartService.addItem(userId!, item),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
      toast.success('カートに追加しました');
    },
    onError: () => {
      toast.error('カートへの追加に失敗しました');
    },
  });

  const updateItemMutation = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: string; quantity: number }) =>
      cartService.updateItem(userId!, itemId, quantity),
    onMutate: async ({ itemId, quantity }) => {
      // Optimistic update
      await queryClient.cancelQueries({ queryKey: CART_QUERY_KEY });
      const previous = queryClient.getQueryData<CartResponse>(CART_QUERY_KEY);

      if (previous) {
        const updated = {
          ...previous,
          items: previous.items.map((item) =>
            item.id === itemId
              ? { ...item, quantity, totalPrice: item.unitPrice * quantity }
              : item,
          ),
        };
        updated.totalAmount = updated.items.reduce((sum, item) => sum + item.totalPrice, 0);
        queryClient.setQueryData(CART_QUERY_KEY, updated);
      }

      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(CART_QUERY_KEY, context.previous);
      }
      toast.error('数量の変更に失敗しました');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
  });

  const removeItemMutation = useMutation({
    mutationFn: (itemId: string) => cartService.removeItem(userId!, itemId),
    onMutate: async (itemId) => {
      // Optimistic update
      await queryClient.cancelQueries({ queryKey: CART_QUERY_KEY });
      const previous = queryClient.getQueryData<CartResponse>(CART_QUERY_KEY);

      if (previous) {
        const updated = {
          ...previous,
          items: previous.items.filter((item) => item.id !== itemId),
        };
        updated.totalAmount = updated.items.reduce((sum, item) => sum + item.totalPrice, 0);
        queryClient.setQueryData(CART_QUERY_KEY, updated);
      }

      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(CART_QUERY_KEY, context.previous);
      }
      toast.error('商品の削除に失敗しました');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
  });

  return {
    cart,
    isLoading,
    error,
    itemCount: cart?.items.length ?? 0,
    addItem: addItemMutation.mutate,
    updateItem: updateItemMutation.mutate,
    removeItem: removeItemMutation.mutate,
    isAddingItem: addItemMutation.isPending,
    isUpdatingItem: updateItemMutation.isPending,
    isRemovingItem: removeItemMutation.isPending,
  };
}
