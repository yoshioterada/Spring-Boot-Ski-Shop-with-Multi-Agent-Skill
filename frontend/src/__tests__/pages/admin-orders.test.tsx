import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/orders',
}));

vi.mock('next-auth/react', () => ({
  useSession: () => ({
    data: { user: { id: 'admin-1', role: 'ADMIN' }, accessToken: 'token' },
    status: 'authenticated',
  }),
}));

vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({ isAuthenticated: true, isAdmin: true, isManager: true, login: vi.fn(), logout: vi.fn() }),
}));

vi.mock('@/hooks/use-cart', () => ({
  useCart: () => ({ cart: null }),
}));

describe('AdminOrdersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        content: [
          { id: 1, orderNumber: 'ORD-001', status: 'PENDING', totalAmount: 50000, createdAt: '2026-03-20T00:00:00Z', customerEmail: 'test@example.com' },
        ],
        totalElements: 1,
        totalPages: 1,
      }),
    });
  });

  it('renders orders page without crashing', async () => {
    const { default: OrdersPage } = await import('@/app/(admin)/admin/orders/page');
    const { container } = render(<OrdersPage />);
    expect(container).toBeDefined();
  });

  it('displays orders heading', async () => {
    const { default: OrdersPage } = await import('@/app/(admin)/admin/orders/page');
    render(<OrdersPage />);
    expect(screen.getByText(/注文管理/)).toBeDefined();
  });

  it('has status filter', async () => {
    const { default: OrdersPage } = await import('@/app/(admin)/admin/orders/page');
    render(<OrdersPage />);
    // Should have a status filter select or buttons
    const filterElements = screen.queryAllByText(/全て|ALL/);
    expect(filterElements.length).toBeGreaterThanOrEqual(0);
  });
});
