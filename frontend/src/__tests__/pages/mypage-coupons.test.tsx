import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/mypage/coupons',
}));

vi.mock('next-auth/react', () => ({
  useSession: () => ({ data: { user: { id: 'u1' }, accessToken: 't' }, status: 'authenticated' }),
}));

vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({ isAuthenticated: true, isAdmin: false }),
}));

vi.mock('@/hooks/use-cart', () => ({
  useCart: () => ({ cart: null }),
}));

describe('CouponsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([
        { id: 'c1', code: 'WINTER20', discountType: 'PERCENTAGE', discountValue: 20, expiresAt: '2026-12-31T23:59:59Z', minOrderAmount: 5000, usedCount: 0, maxUsageCount: 1 },
      ]),
    });
  });

  it('renders coupons page without crashing', async () => {
    const { default: CouponsPage } = await import('@/app/(ec)/mypage/coupons/page');
    const { container } = render(<CouponsPage />);
    expect(container).toBeDefined();
  });

  it('displays coupons heading', async () => {
    const { default: CouponsPage } = await import('@/app/(ec)/mypage/coupons/page');
    render(<CouponsPage />);
    expect(screen.getByText(/クーポン/)).toBeDefined();
  });
});
