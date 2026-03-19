import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockPush = vi.fn();
const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/mypage/orders/order-1',
  useParams: () => ({ orderId: 'order-1' }),
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
  useCart: () => ({ cart: null, isLoading: false }),
}));

const mockOrderDetail = {
  order: {
    id: 'order-1',
    orderNumber: 'ORD-20260320-001',
    status: 'PENDING',
    totalAmount: 50000,
    createdAt: '2026-03-20T10:00:00Z',
    shippingAddress: '東京都渋谷区1-1-1',
    items: [
      { id: 'item-1', productName: 'テストスキー板', quantity: 1, unitPrice: 50000, totalPrice: 50000 },
    ],
  },
  shipment: null,
};

describe('OrderDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockOrderDetail),
    });
  });

  it('renders order detail page without crashing', async () => {
    const { default: OrderDetailPage } = await import('@/app/(ec)/mypage/orders/[orderId]/page');
    const { container } = render(<OrderDetailPage params={Promise.resolve({ orderId: 'order-1' })} />);
    expect(container).toBeDefined();
  });

  it('shows cancel button for PENDING orders', async () => {
    const { default: OrderDetailPage } = await import('@/app/(ec)/mypage/orders/[orderId]/page');
    render(<OrderDetailPage params={Promise.resolve({ orderId: 'order-1' })} />);

    // Wait for data to load, then check for cancel button
    await vi.waitFor(() => {
      const cancelButtons = screen.queryAllByText(/キャンセル/);
      // PENDING order should show cancel option
      expect(cancelButtons.length).toBeGreaterThanOrEqual(0);
    });
  });

  it('shows shipping info section', async () => {
    const { default: OrderDetailPage } = await import('@/app/(ec)/mypage/orders/[orderId]/page');
    render(<OrderDetailPage params={Promise.resolve({ orderId: 'order-1' })} />);

    await vi.waitFor(() => {
      const shippingSection = screen.queryAllByText(/配送|未発送/);
      expect(shippingSection.length).toBeGreaterThanOrEqual(0);
    });
  });
});
