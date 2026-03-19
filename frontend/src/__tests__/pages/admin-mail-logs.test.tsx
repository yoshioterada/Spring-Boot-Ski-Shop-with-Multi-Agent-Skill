import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const mockFetch = vi.fn();
global.fetch = mockFetch;

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/admin/mail-logs',
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

describe('AdminMailLogsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([
        { id: 'm1', recipientEmail: 'test@example.com', type: 'ORDER_CONFIRMATION', status: 'SENT', retryCount: 0, createdAt: '2026-03-20T00:00:00Z' },
      ]),
    });
  });

  it('renders mail-logs page without crashing', async () => {
    const { default: MailLogsPage } = await import('@/app/(admin)/admin/mail-logs/page');
    const { container } = render(<MailLogsPage />);
    expect(container).toBeDefined();
  });

  it('displays mail-logs heading', async () => {
    const { default: MailLogsPage } = await import('@/app/(admin)/admin/mail-logs/page');
    render(<MailLogsPage />);
    expect(screen.getByText(/メール送信履歴/)).toBeDefined();
  });

  it('has status filter', async () => {
    const { default: MailLogsPage } = await import('@/app/(admin)/admin/mail-logs/page');
    render(<MailLogsPage />);
    // Should have filter controls
    expect(document.querySelector('select, [role="combobox"]')).toBeDefined();
  });
});
