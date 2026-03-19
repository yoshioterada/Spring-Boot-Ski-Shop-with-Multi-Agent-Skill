import { render } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockPush = vi.fn();
const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/checkout',
}));

vi.mock('next-auth/react', () => ({
  useSession: () => ({
    data: { user: { id: 'user-1', email: 'test@example.com' }, accessToken: 'token' },
    status: 'authenticated',
  }),
}));

vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isAdmin: false,
    user: { id: 'user-1' },
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/hooks/use-cart', () => ({
  useCart: () => ({
    cart: {
      items: [
        { id: 'item-1', productId: 'p1', productName: 'テスト商品', unitPrice: 10000, quantity: 1, totalPrice: 10000 },
      ],
      totalAmount: 10000,
    },
    isLoading: false,
    addItem: vi.fn(),
  }),
}));

describe('CheckoutPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve({
          cart: { items: [{ id: 'item-1', unitPrice: 10000, quantity: 1, totalPrice: 10000 }], totalAmount: 10000 },
          points: { currentBalance: 100 },
        }),
    });
  });

  it('renders checkout page without crashing', async () => {
    const { default: CheckoutPage } = await import('@/app/(ec)/checkout/page');
    const { container } = render(<CheckoutPage />);
    expect(container).toBeDefined();
  });
});
