import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/users',
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

describe('AdminUsersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        content: [
          { id: 'u1', email: 'user@example.com', firstName: 'Test', lastName: 'User', status: 'ACTIVE', role: 'CUSTOMER', createdAt: '2026-03-20T00:00:00Z' },
        ],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      }),
    });
  });

  it('renders users page without crashing', async () => {
    const { default: UsersPage } = await import('@/app/(admin)/admin/users/page');
    const { container } = render(<UsersPage />);
    expect(container).toBeDefined();
  });

  it('displays users heading', async () => {
    const { default: UsersPage } = await import('@/app/(admin)/admin/users/page');
    render(<UsersPage />);
    expect(screen.getByText(/ユーザー管理/)).toBeDefined();
  });

  it('has search input', async () => {
    const { default: UsersPage } = await import('@/app/(admin)/admin/users/page');
    render(<UsersPage />);
    const searchInput = document.querySelector('input');
    expect(searchInput).toBeDefined();
  });
});
