import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockPush = vi.fn();
const mockRefresh = vi.fn();
const mockSignIn = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), back: vi.fn(), refresh: mockRefresh }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/login',
}));

vi.mock('next-auth/react', () => ({
  signIn: (...args: unknown[]) => mockSignIn(...args),
  useSession: () => ({ data: null, status: 'unauthenticated' }),
}));

vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({ isAuthenticated: false, login: vi.fn(), logout: vi.fn() }),
}));

vi.mock('@/hooks/use-cart', () => ({
  useCart: () => ({ cart: null }),
}));

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders login form with email and password fields', async () => {
    const { default: LoginPage } = await import('@/app/(auth)/login/page');
    render(<LoginPage />);

    expect(screen.getByLabelText(/メールアドレス/)).toBeDefined();
    expect(screen.getByLabelText(/パスワード/)).toBeDefined();
    expect(screen.getByRole('button', { name: /ログイン/ })).toBeDefined();
  });

  it('shows validation error for invalid email', async () => {
    const { default: LoginPage } = await import('@/app/(auth)/login/page');
    render(<LoginPage />);
    const user = userEvent.setup();

    const emailInput = screen.getByLabelText(/メールアドレス/);
    await user.type(emailInput, 'invalid');
    await user.tab();

    await waitFor(() => {
      expect(screen.getByText(/有効なメールアドレス/)).toBeDefined();
    });
  });

  it('shows error message on failed login', async () => {
    mockSignIn.mockResolvedValue({ error: 'CredentialsSignin' });

    const { default: LoginPage } = await import('@/app/(auth)/login/page');
    render(<LoginPage />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/メールアドレス/), 'test@example.com');
    await user.type(screen.getByLabelText(/パスワード/), 'wrongpass');
    await user.click(screen.getByRole('button', { name: /ログイン/ }));

    await waitFor(() => {
      expect(screen.getByText(/正しくありません/)).toBeDefined();
    });
  });

  it('redirects to home on successful login', async () => {
    mockSignIn.mockResolvedValue({ error: null });

    const { default: LoginPage } = await import('@/app/(auth)/login/page');
    render(<LoginPage />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/メールアドレス/), 'test@example.com');
    await user.type(screen.getByLabelText(/パスワード/), 'Test1234!');
    await user.click(screen.getByRole('button', { name: /ログイン/ }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith('/');
    });
  });

  it('has link to register page', async () => {
    const { default: LoginPage } = await import('@/app/(auth)/login/page');
    render(<LoginPage />);

    expect(screen.getByText(/新規登録/)).toBeDefined();
  });

  it('has link to forgot password page', async () => {
    const { default: LoginPage } = await import('@/app/(auth)/login/page');
    render(<LoginPage />);

    expect(screen.getByText(/パスワードをお忘れ/)).toBeDefined();
  });
});
