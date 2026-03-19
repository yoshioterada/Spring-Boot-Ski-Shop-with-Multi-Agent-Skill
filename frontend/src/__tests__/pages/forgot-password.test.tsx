import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/forgot-password',
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

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders email input form', async () => {
    const { default: ForgotPasswordPage } = await import('@/app/(auth)/forgot-password/page');
    render(<ForgotPasswordPage />);

    expect(screen.getByLabelText(/メールアドレス/)).toBeDefined();
    expect(screen.getByRole('button', { name: /送信/ })).toBeDefined();
  });

  it('shows success message after submission', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ status: 'ok' }),
    });

    const { default: ForgotPasswordPage } = await import('@/app/(auth)/forgot-password/page');
    render(<ForgotPasswordPage />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/メールアドレス/), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /送信/ }));

    await waitFor(() => {
      expect(screen.getByText(/メールを送信しました/)).toBeDefined();
    });
  });

  it('shows success even on API error (email enumeration prevention)', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      json: () => Promise.resolve({ detail: 'Not found' }),
    });

    const { default: ForgotPasswordPage } = await import('@/app/(auth)/forgot-password/page');
    render(<ForgotPasswordPage />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/メールアドレス/), 'nonexistent@example.com');
    await user.click(screen.getByRole('button', { name: /送信/ }));

    await waitFor(() => {
      expect(screen.getByText(/メールを送信しました/)).toBeDefined();
    });
  });

  it('has link to login page', async () => {
    const { default: ForgotPasswordPage } = await import('@/app/(auth)/forgot-password/page');
    render(<ForgotPasswordPage />);

    expect(screen.getByText(/ログインに戻る/)).toBeDefined();
  });
});
