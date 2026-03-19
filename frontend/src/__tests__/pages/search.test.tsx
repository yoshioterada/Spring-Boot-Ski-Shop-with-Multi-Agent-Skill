import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

// Mock next/navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams('q=ski'),
  usePathname: () => '/search',
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

// Mock fetch for search results
global.fetch = vi.fn().mockResolvedValue({
  ok: true,
  json: () =>
    Promise.resolve({
      results: [],
      totalResults: 0,
      query: 'ski',
    }),
});

describe('SearchPage', () => {
  it('renders search page without crashing', async () => {
    const { default: SearchPage } = await import(
      '@/app/(ec)/search/page'
    );
    const { container } = render(<SearchPage />);
    expect(container).toBeDefined();
  });

  it('displays search query in breadcrumb', async () => {
    const { default: SearchPage } = await import(
      '@/app/(ec)/search/page'
    );
    render(<SearchPage />);

    // Breadcrumb should show search query
    expect(screen.getByText(/ski/)).toBeDefined();
  });
});
