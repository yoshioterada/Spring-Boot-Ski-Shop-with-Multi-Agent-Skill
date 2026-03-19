import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/analytics',
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

describe('AdminAnalyticsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ sales: [], users: [], trends: [] }),
    });
  });

  it('renders analytics page without crashing', async () => {
    const { default: AnalyticsPage } = await import('@/app/(admin)/admin/analytics/page');
    const { container } = render(<AnalyticsPage />);
    expect(container).toBeDefined();
  });

  it('displays analytics heading', async () => {
    const { default: AnalyticsPage } = await import('@/app/(admin)/admin/analytics/page');
    render(<AnalyticsPage />);
    expect(screen.getByText(/分析/)).toBeDefined();
  });

  it('has multiple tabs for different analysis views', async () => {
    const { default: AnalyticsPage } = await import('@/app/(admin)/admin/analytics/page');
    render(<AnalyticsPage />);
    const tabs = screen.queryAllByRole('tab');
    expect(tabs.length).toBeGreaterThanOrEqual(2);
  });

  it('has report generation section', async () => {
    const { default: AnalyticsPage } = await import('@/app/(admin)/admin/analytics/page');
    render(<AnalyticsPage />);
    const reportElements = screen.queryAllByText(/レポート/);
    expect(reportElements.length).toBeGreaterThanOrEqual(1);
  });
});
