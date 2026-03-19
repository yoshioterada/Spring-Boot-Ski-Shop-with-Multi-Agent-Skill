import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/points',
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

describe('AdminPointsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        tiers: [{ id: 'BRONZE', name: 'ブロンズ', minPoints: 0 }],
        search: [],
      }),
    });
  });

  it('renders points page without crashing', async () => {
    const { default: PointsPage } = await import('@/app/(admin)/admin/points/page');
    const { container } = render(<PointsPage />);
    expect(container).toBeDefined();
  });

  it('displays points heading', async () => {
    const { default: PointsPage } = await import('@/app/(admin)/admin/points/page');
    render(<PointsPage />);
    expect(screen.getByText(/ポイント管理/)).toBeDefined();
  });
});
