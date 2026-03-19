import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/register',
}));

vi.mock('next-auth/react', () => ({
  useSession: () => ({ data: null, status: 'unauthenticated' }),
}));

vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({ isAuthenticated: false, login: vi.fn(), logout: vi.fn() }),
}));

vi.mock('@/hooks/use-cart', () => ({
  useCart: () => ({ cart: null }),
}));

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders registration form with all required fields', async () => {
    const { default: RegisterPage } = await import('@/app/(auth)/register/page');
    render(<RegisterPage />);

    expect(screen.getByLabelText(/姓/)).toBeDefined();
    expect(screen.getByLabelText(/名/)).toBeDefined();
    expect(screen.getByLabelText(/メールアドレス/)).toBeDefined();
    expect(screen.getAllByLabelText(/パスワード/).length).toBeGreaterThanOrEqual(1);
  });

  it('shows password strength indicator when password is entered', async () => {
    const { default: RegisterPage } = await import('@/app/(auth)/register/page');
    render(<RegisterPage />);
    const user = userEvent.setup();

    const passwordInputs = screen.getAllByLabelText(/パスワード/);
    const passwordInput = passwordInputs[0];
    await user.type(passwordInput, 'Test1234!');

    await waitFor(() => {
      expect(screen.getByText(/パスワード強度/)).toBeDefined();
    });
  });

  it('shows success message after successful registration', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ userId: '123' }),
    });

    const { default: RegisterPage } = await import('@/app/(auth)/register/page');
    render(<RegisterPage />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/姓/), '寺田');
    await user.type(screen.getByLabelText(/名/), '佳央');
    await user.type(screen.getByLabelText(/メールアドレス/), 'test@example.com');

    const passwordInputs = screen.getAllByLabelText(/パスワード/);
    await user.type(passwordInputs[0], 'Test1234!');
    await user.type(passwordInputs[1], 'Test1234!');

    await user.click(screen.getByRole('button', { name: /登録する/ }));

    await waitFor(() => {
      expect(screen.getByText(/認証メールを送信しました/)).toBeDefined();
    });
  });

  it('shows email already exists error on 409', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 409,
      json: () => Promise.resolve({ errorCode: 'EMAIL_ALREADY_EXISTS' }),
    });

    const { default: RegisterPage } = await import('@/app/(auth)/register/page');
    render(<RegisterPage />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/姓/), '寺田');
    await user.type(screen.getByLabelText(/名/), '佳央');
    await user.type(screen.getByLabelText(/メールアドレス/), 'existing@example.com');

    const passwordInputs = screen.getAllByLabelText(/パスワード/);
    await user.type(passwordInputs[0], 'Test1234!');
    await user.type(passwordInputs[1], 'Test1234!');

    await user.click(screen.getByRole('button', { name: /登録する/ }));

    await waitFor(() => {
      expect(screen.getByText(/既に登録されています/)).toBeDefined();
    });
  });

  it('handles 409 status without errorCode field', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 409,
      json: () => Promise.resolve({}),
    });

    const { default: RegisterPage } = await import('@/app/(auth)/register/page');
    render(<RegisterPage />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/姓/), '寺田');
    await user.type(screen.getByLabelText(/名/), '佳央');
    await user.type(screen.getByLabelText(/メールアドレス/), 'existing@example.com');

    const passwordInputs = screen.getAllByLabelText(/パスワード/);
    await user.type(passwordInputs[0], 'Test1234!');
    await user.type(passwordInputs[1], 'Test1234!');

    await user.click(screen.getByRole('button', { name: /登録する/ }));

    await waitFor(() => {
      expect(screen.getByText(/既に登録されています/)).toBeDefined();
    });
  });

  it('has link to login page', async () => {
    const { default: RegisterPage } = await import('@/app/(auth)/register/page');
    render(<RegisterPage />);

    expect(screen.getByText(/ログイン/)).toBeDefined();
  });
});
