import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/campaigns',
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

describe('AdminCampaignsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([
        { id: 'c1', name: 'ウィンターセール', status: 'ACTIVE', startDate: '2026-01-01', endDate: '2026-03-31' },
      ]),
    });
  });

  it('renders campaigns page without crashing', async () => {
    const { default: CampaignsPage } = await import('@/app/(admin)/admin/campaigns/page');
    const { container } = render(<CampaignsPage />);
    expect(container).toBeDefined();
  });

  it('displays campaigns heading', async () => {
    const { default: CampaignsPage } = await import('@/app/(admin)/admin/campaigns/page');
    render(<CampaignsPage />);
    expect(screen.getByText(/キャンペーン管理/)).toBeDefined();
  });
});
