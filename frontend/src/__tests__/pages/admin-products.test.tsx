import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/products',
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

describe('AdminProductsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        content: [
          { id: 'p1', name: 'テストスキー板', sku: 'SKU-001', regularPrice: 50000, availableQuantity: 10, status: 'ACTIVE' },
        ],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      }),
    });
  });

  it('renders products page without crashing', async () => {
    const { default: ProductsPage } = await import('@/app/(admin)/admin/products/page');
    const { container } = render(<ProductsPage />);
    expect(container).toBeDefined();
  });

  it('displays products heading', async () => {
    const { default: ProductsPage } = await import('@/app/(admin)/admin/products/page');
    render(<ProductsPage />);
    expect(screen.getByText(/商品管理/)).toBeDefined();
  });

  it('has new product button', async () => {
    const { default: ProductsPage } = await import('@/app/(admin)/admin/products/page');
    render(<ProductsPage />);
    expect(screen.getByText(/新規/)).toBeDefined();
  });
});
