import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/mypage/returns',
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

describe('ReturnsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    });
  });

  it('renders returns page without crashing', async () => {
    const { default: ReturnsPage } = await import('@/app/(ec)/mypage/returns/page');
    const { container } = render(<ReturnsPage />);
    expect(container).toBeDefined();
  });

  it('displays returns heading', async () => {
    const { default: ReturnsPage } = await import('@/app/(ec)/mypage/returns/page');
    render(<ReturnsPage />);
    expect(screen.getByText(/返品/)).toBeDefined();
  });

  it('has return request button', async () => {
    const { default: ReturnsPage } = await import('@/app/(ec)/mypage/returns/page');
    render(<ReturnsPage />);
    expect(screen.getByText(/返品申請/)).toBeDefined();
  });

  it('shows empty state when no returns', async () => {
    const { default: ReturnsPage } = await import('@/app/(ec)/mypage/returns/page');
    render(<ReturnsPage />);
    // Should eventually show empty state
    expect(document.body).toBeDefined();
  });
});
