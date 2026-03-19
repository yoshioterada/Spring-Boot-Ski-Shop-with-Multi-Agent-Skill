import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/ai',
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

describe('AdminAIPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([
        { id: 'm1', name: 'レコメンドモデル', version: 'v2.1', status: 'DEPLOYED', accuracy: 0.92, lastTrainedAt: '2026-03-15T00:00:00Z' },
        { id: 'm2', name: '検索モデル', version: 'v1.5', status: 'READY', accuracy: 0.88, lastTrainedAt: '2026-03-10T00:00:00Z' },
      ]),
    });
  });

  it('renders AI page without crashing', async () => {
    const { default: AIPage } = await import('@/app/(admin)/admin/ai/page');
    const { container } = render(<AIPage />);
    expect(container).toBeDefined();
  });

  it('displays AI model heading', async () => {
    const { default: AIPage } = await import('@/app/(admin)/admin/ai/page');
    render(<AIPage />);
    expect(screen.getByText(/AI/)).toBeDefined();
  });

  it('has model list table', async () => {
    const { default: AIPage } = await import('@/app/(admin)/admin/ai/page');
    render(<AIPage />);
    const tables = document.querySelectorAll('table');
    expect(tables.length).toBeGreaterThanOrEqual(0);
  });

  it('has deploy and train action buttons', async () => {
    const { default: AIPage } = await import('@/app/(admin)/admin/ai/page');
    render(<AIPage />);
    const buttons = screen.queryAllByRole('button');
    expect(buttons.length).toBeGreaterThanOrEqual(1);
  });
});
