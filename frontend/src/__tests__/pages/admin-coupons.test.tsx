import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/coupons',
}));

vi.mock('next-auth/react', () => ({
  useSession: () => ({ data: { user: { id: 'a1', role: 'ADMIN' }, accessToken: 't' }, status: 'authenticated' }),
}));

vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({ isAuthenticated: true, isAdmin: true, isManager: true }),
}));

vi.mock('@/hooks/use-cart', () => ({
  useCart: () => ({ cart: null }),
}));

describe('AdminCouponsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([
        { id: 'cp1', code: 'WINTER20', discountType: 'PERCENTAGE', discountValue: 20, usageCount: 5, maxUses: 100 },
      ]),
    });
  });

  it('renders coupons page without crashing', async () => {
    const { default: CouponsPage } = await import('@/app/(admin)/admin/coupons/page');
    const { container } = render(<CouponsPage />);
    expect(container).toBeDefined();
  });

  it('displays coupons heading', async () => {
    const { default: CouponsPage } = await import('@/app/(admin)/admin/coupons/page');
    render(<CouponsPage />);
    expect(screen.getByText(/クーポン管理/)).toBeDefined();
  });
});
