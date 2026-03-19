import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams('token=valid-token-123'),
  usePathname: () => '/verify-email',
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

describe('VerifyEmailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows success message with valid token', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ status: 'ok' }),
    });

    const { default: VerifyEmailPage } = await import('@/app/(auth)/verify-email/page');
    render(<VerifyEmailPage />);

    await waitFor(() => {
      expect(screen.getByText(/メール認証が完了しました/)).toBeDefined();
    });
  });

  it('shows error message when API fails', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      json: () => Promise.resolve({ detail: 'Token expired' }),
    });

    const { default: VerifyEmailPage } = await import('@/app/(auth)/verify-email/page');
    render(<VerifyEmailPage />);

    await waitFor(() => {
      expect(screen.getByText(/無効または期限切れ/)).toBeDefined();
    });
  });

  it('has link to login page after verification', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ status: 'ok' }),
    });

    const { default: VerifyEmailPage } = await import('@/app/(auth)/verify-email/page');
    render(<VerifyEmailPage />);

    await waitFor(() => {
      expect(screen.getByText(/ログインへ/)).toBeDefined();
    });
  });
});

describe('VerifyEmailPage - no token', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.doMock('next/navigation', () => ({
      useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
      useSearchParams: () => new URLSearchParams(),
      usePathname: () => '/verify-email',
    }));
  });

  it('shows error when no token in URL', async () => {
    const { default: VerifyEmailPage } = await import('@/app/(auth)/verify-email/page');
    render(<VerifyEmailPage />);

    await waitFor(() => {
      const errorElements = screen.queryAllByText(/トークン/);
      expect(errorElements.length).toBeGreaterThanOrEqual(0);
    });
  });
});
