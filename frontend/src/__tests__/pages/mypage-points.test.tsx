import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/mypage/points',
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

describe('PointsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        balance: { currentBalance: 1500, totalEarned: 5000 },
        tier: { currentTier: 'SILVER', totalPoints: 1500, nextTier: 'GOLD', pointsToNext: 3500 },
        history: { content: [], page: { number: 0, totalPages: 0, totalElements: 0 } },
        expiring: [],
      }),
    });
  });

  it('renders points page without crashing', async () => {
    const { default: PointsPage } = await import('@/app/(ec)/mypage/points/page');
    const { container } = render(<PointsPage />);
    expect(container).toBeDefined();
  });

  it('displays points heading', async () => {
    const { default: PointsPage } = await import('@/app/(ec)/mypage/points/page');
    render(<PointsPage />);
    expect(screen.getByText(/ポイント/)).toBeDefined();
  });
});
