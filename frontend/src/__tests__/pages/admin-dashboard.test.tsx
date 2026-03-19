import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/dashboard',
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

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        kpi: { todaySales: 150000, orderCount: 12, newMembers: 3, activeUsers: 45 },
        revenueChart: [],
        lowStock: [],
        recentOrders: [],
      }),
    });
  });

  it('renders dashboard without crashing', async () => {
    const { default: DashboardPage } = await import('@/app/(admin)/admin/dashboard/page');
    const { container } = render(<DashboardPage />);
    expect(container).toBeDefined();
  });

  it('displays dashboard heading', async () => {
    const { default: DashboardPage } = await import('@/app/(admin)/admin/dashboard/page');
    render(<DashboardPage />);
    expect(screen.getByText(/ダッシュボード/)).toBeDefined();
  });
});
