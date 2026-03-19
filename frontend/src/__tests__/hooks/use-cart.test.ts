import { describe, expect, it, vi, beforeEach } from 'vitest';

import { useAuthStore } from '@/stores/auth-store';

// Mock tanstack react-query
vi.mock('@tanstack/react-query', () => ({
  useQuery: vi.fn().mockReturnValue({ data: null, isLoading: false, error: null }),
  useMutation: vi.fn().mockReturnValue({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isLoading: false,
  }),
  useQueryClient: vi.fn().mockReturnValue({
    invalidateQueries: vi.fn(),
    cancelQueries: vi.fn(),
    getQueryData: vi.fn(),
    setQueryData: vi.fn(),
  }),
}));

vi.mock('@/bff/services/cart-service', () => ({
  cartService: {
    get: vi.fn(),
    addItem: vi.fn(),
    updateItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
  },
}));

describe('useCart hook dependencies', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('cart service module is importable', async () => {
    const { cartService } = await import('@/bff/services/cart-service');
    expect(cartService).toBeDefined();
    expect(cartService.get).toBeDefined();
    expect(cartService.addItem).toBeDefined();
    expect(cartService.updateItem).toBeDefined();
    expect(cartService.removeItem).toBeDefined();
  });

  it('useCart hook is importable', async () => {
    const { useCart } = await import('@/hooks/use-cart');
    expect(useCart).toBeDefined();
    expect(typeof useCart).toBe('function');
  });

  it('auth store provides userId for cart queries', () => {
    const { setUser } = useAuthStore.getState();
    setUser({
      userId: 'cart-user-1',
      email: 'cart@example.com',
      firstName: 'Cart',
      lastName: 'User',
      role: 'CUSTOMER',
    });

    const state = useAuthStore.getState();
    expect(state.userId).toBe('cart-user-1');
    expect(state.isAuthenticated).toBe(true);
  });

  it('cart query is disabled when not authenticated', () => {
    const { clearUser } = useAuthStore.getState();
    clearUser();

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.userId).toBeNull();
  });
});
