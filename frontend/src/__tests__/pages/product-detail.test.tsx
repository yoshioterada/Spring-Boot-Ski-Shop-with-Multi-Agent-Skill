import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

// Mock next/navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/product/123',
}));

// Mock next-auth
vi.mock('next-auth/react', () => ({
  useSession: () => ({ data: null, status: 'unauthenticated' }),
  SessionProvider: ({ children }: { children: React.ReactNode }) => children,
}));

// Mock hooks
vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({
    isAuthenticated: false,
    isAdmin: false,
    user: null,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/hooks/use-cart', () => ({
  useCart: () => ({
    cart: null,
    isLoading: false,
    addItem: vi.fn(),
  }),
}));

// Mock fetch for product detail
const mockProduct = {
  id: '123',
  name: 'テストスキー板',
  brand: 'TestBrand',
  sku: 'SKU-001',
  description: 'テスト商品説明',
  regularPrice: 50000,
  salePrice: null,
  availableQuantity: 10,
  category: { id: 'ski', name: 'スキー板' },
  images: [],
  specifications: {},
};

global.fetch = vi.fn().mockResolvedValue({
  ok: true,
  json: () =>
    Promise.resolve({
      product: mockProduct,
      similar: [],
    }),
});

describe('ProductDetailPage', () => {
  it('renders product detail page without crashing', async () => {
    // Dynamic import to allow mocks to be set up first
    const { default: ProductDetailPage } = await import(
      '@/app/(ec)/product/[productId]/page'
    );
    const { container } = render(
      <ProductDetailPage params={Promise.resolve({ productId: '123' })} />,
    );
    expect(container).toBeDefined();
  });
});
