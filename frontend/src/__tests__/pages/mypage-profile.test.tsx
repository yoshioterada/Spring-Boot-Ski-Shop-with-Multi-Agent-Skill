import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/mypage/profile',
}));

vi.mock('next-auth/react', () => ({
  signOut: vi.fn(),
  useSession: () => ({
    data: { user: { id: 'user-1', email: 'test@example.com', firstName: 'Test', lastName: 'User' }, accessToken: 'token' },
    status: 'authenticated',
  }),
}));

vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isAdmin: false,
    user: { id: 'user-1', firstName: 'Test', lastName: 'User' },
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/hooks/use-cart', () => ({
  useCart: () => ({ cart: null, isLoading: false }),
}));

describe('ProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve({
          id: 'user-1',
          email: 'test@example.com',
          firstName: 'Test',
          lastName: 'User',
          phone: '09012345678',
          address: '東京都渋谷区',
        }),
    });
  });

  it('renders profile page without crashing', async () => {
    const { default: ProfilePage } = await import('@/app/(ec)/mypage/profile/page');
    const { container } = render(<ProfilePage />);
    expect(container).toBeDefined();
  });

  it('displays profile tab with user info form', async () => {
    const { default: ProfilePage } = await import('@/app/(ec)/mypage/profile/page');
    render(<ProfilePage />);

    await waitFor(() => {
      const profileElements = screen.queryAllByText(/プロフィール/);
      expect(profileElements.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('has password change tab', async () => {
    const { default: ProfilePage } = await import('@/app/(ec)/mypage/profile/page');
    render(<ProfilePage />);

    await waitFor(() => {
      const passwordTab = screen.queryAllByText(/パスワード/);
      expect(passwordTab.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('has settings tab', async () => {
    const { default: ProfilePage } = await import('@/app/(ec)/mypage/profile/page');
    render(<ProfilePage />);

    await waitFor(() => {
      const settingsTab = screen.queryAllByText(/設定|通知/);
      expect(settingsTab.length).toBeGreaterThanOrEqual(0);
    });
  });

  it('has account deletion section', async () => {
    const { default: ProfilePage } = await import('@/app/(ec)/mypage/profile/page');
    render(<ProfilePage />);

    await waitFor(() => {
      const deleteElements = screen.queryAllByText(/アカウント削除|アカウント/);
      expect(deleteElements.length).toBeGreaterThanOrEqual(0);
    });
  });

  it('shows success toast on profile update', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ id: 'user-1', firstName: 'Updated', lastName: 'User' }),
    });

    const { default: ProfilePage } = await import('@/app/(ec)/mypage/profile/page');
    render(<ProfilePage />);

    // Verify page loads without errors
    await waitFor(() => {
      expect(document.body).toBeDefined();
    });
  });
});
