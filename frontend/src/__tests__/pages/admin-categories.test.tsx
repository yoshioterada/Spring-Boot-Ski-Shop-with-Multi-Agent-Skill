import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/categories',
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

describe('AdminCategoriesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([
        { id: 'cat-1', name: 'スキー板', description: 'スキー板カテゴリ', displayOrder: 1, active: true },
      ]),
    });
  });

  it('renders categories page without crashing', async () => {
    const { default: CategoriesPage } = await import('@/app/(admin)/admin/categories/page');
    const { container } = render(<CategoriesPage />);
    expect(container).toBeDefined();
  });

  it('displays categories heading', async () => {
    const { default: CategoriesPage } = await import('@/app/(admin)/admin/categories/page');
    render(<CategoriesPage />);
    expect(screen.getByText(/カテゴリ管理/)).toBeDefined();
  });
});
