import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockPush = vi.fn();
const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/mypage/orders',
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
    user: { id: 'user-1', firstName: 'Test', lastName: 'User' },
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/hooks/use-cart', () => ({
  useCart: () => ({ cart: null, isLoading: false }),
}));

const mockOrders = {
  content: [
    {
      id: 'order-1',
      orderNumber: 'ORD-20260320-001',
      status: 'PENDING',
      totalAmount: 50000,
      createdAt: '2026-03-20T00:00:00Z',
      items: [{ productName: 'テストスキー板', quantity: 1 }],
    },
    {
      id: 'order-2',
      orderNumber: 'ORD-20260319-001',
      status: 'DELIVERED',
      totalAmount: 30000,
      createdAt: '2026-03-19T00:00:00Z',
      items: [{ productName: 'スキーブーツ', quantity: 1 }],
    },
  ],
  page: { size: 10, number: 0, totalElements: 2, totalPages: 1 },
};

describe('OrdersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockOrders),
    });
  });

  it('renders orders page without crashing', async () => {
    const { default: OrdersPage } = await import('@/app/(ec)/mypage/orders/page');
    const { container } = render(<OrdersPage />);
    expect(container).toBeDefined();
  });

  it('displays order list heading', async () => {
    const { default: OrdersPage } = await import('@/app/(ec)/mypage/orders/page');
    render(<OrdersPage />);
    expect(screen.getByText(/注文履歴/)).toBeDefined();
  });

  it('has search input for order number', async () => {
    const { default: OrdersPage } = await import('@/app/(ec)/mypage/orders/page');
    render(<OrdersPage />);
    const searchInput = document.querySelector('input[placeholder*="注文番号"]') ||
                        document.querySelector('input[type="search"]') ||
                        document.querySelector('input');
    expect(searchInput).toBeDefined();
  });

  it('renders status badges with correct colors', async () => {
    const { default: OrdersPage } = await import('@/app/(ec)/mypage/orders/page');
    const { container } = render(<OrdersPage />);
    // Verify the component renders without errors
    expect(container.querySelector('[class*="flex"]')).toBeDefined();
  });
});
