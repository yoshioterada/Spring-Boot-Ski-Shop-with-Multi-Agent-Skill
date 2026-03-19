import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/inventory',
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

describe('AdminInventoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        content: [{ productId: 'p1', productName: 'テストスキー板', sku: 'SKU-001', currentStock: 5, reservedStock: 0, availableStock: 5, lowStockThreshold: 10 }],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      }),
    });
  });

  it('renders inventory page without crashing', async () => {
    const { default: InventoryPage } = await import('@/app/(admin)/admin/inventory/page');
    const { container } = render(<InventoryPage />);
    expect(container).toBeDefined();
  });

  it('displays inventory heading', async () => {
    const { default: InventoryPage } = await import('@/app/(admin)/admin/inventory/page');
    render(<InventoryPage />);
    expect(screen.getByText(/在庫管理/)).toBeDefined();
  });
});
