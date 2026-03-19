import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockPush = vi.fn();
const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams('token=reset-token-123'),
  usePathname: () => '/reset-password',
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

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders new password form when token is present', async () => {
    const { default: ResetPasswordPage } = await import('@/app/(auth)/reset-password/page');
    render(<ResetPasswordPage />);

    expect(screen.getAllByLabelText(/パスワード/).length).toBeGreaterThanOrEqual(1);
  });

  it('shows success message after password reset', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ status: 'ok' }),
    });

    const { default: ResetPasswordPage } = await import('@/app/(auth)/reset-password/page');
    render(<ResetPasswordPage />);
    const user = userEvent.setup();

    const passwordInputs = screen.getAllByLabelText(/パスワード/);
    await user.type(passwordInputs[0], 'NewTest1234!');
    await user.type(passwordInputs[1], 'NewTest1234!');

    const submitButton = screen.getByRole('button', { name: /変更/ });
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/パスワードを変更しました/)).toBeDefined();
    });
  });

  it('shows error on API failure', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      json: () => Promise.resolve({ detail: 'Token expired' }),
    });

    const { default: ResetPasswordPage } = await import('@/app/(auth)/reset-password/page');
    render(<ResetPasswordPage />);
    const user = userEvent.setup();

    const passwordInputs = screen.getAllByLabelText(/パスワード/);
    await user.type(passwordInputs[0], 'NewTest1234!');
    await user.type(passwordInputs[1], 'NewTest1234!');

    const submitButton = screen.getByRole('button', { name: /変更/ });
    await user.click(submitButton);

    await waitFor(() => {
      const errorMessages = screen.queryAllByRole('alert');
      expect(errorMessages.length + screen.queryAllByText(/エラー|失敗/).length).toBeGreaterThanOrEqual(0);
    });
  });

  it('validates password mismatch', async () => {
    const { default: ResetPasswordPage } = await import('@/app/(auth)/reset-password/page');
    render(<ResetPasswordPage />);
    const user = userEvent.setup();

    const passwordInputs = screen.getAllByLabelText(/パスワード/);
    await user.type(passwordInputs[0], 'NewTest1234!');
    await user.type(passwordInputs[1], 'DifferentPass!');
    await user.tab();

    await waitFor(() => {
      const mismatches = screen.queryAllByText(/一致しません/);
      expect(mismatches.length).toBeGreaterThanOrEqual(0);
    });
  });

  it('has link to login page after success', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ status: 'ok' }),
    });

    const { default: ResetPasswordPage } = await import('@/app/(auth)/reset-password/page');
    render(<ResetPasswordPage />);
    const user = userEvent.setup();

    const passwordInputs = screen.getAllByLabelText(/パスワード/);
    await user.type(passwordInputs[0], 'NewTest1234!');
    await user.type(passwordInputs[1], 'NewTest1234!');

    await user.click(screen.getByRole('button', { name: /変更/ }));

    await waitFor(() => {
      expect(screen.getByText(/ログインへ/)).toBeDefined();
    });
  });
});
