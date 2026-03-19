import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ErrorPlaceholder } from '@/components/common/error-placeholder';

describe('ErrorPlaceholder', () => {
  it('renders error message', () => {
    render(<ErrorPlaceholder message="テストエラー" />);
    expect(screen.getByText('テストエラー')).toBeDefined();
  });

  it('renders retry button when onRetry is provided', () => {
    render(<ErrorPlaceholder message="エラー" onRetry={() => {}} />);
    const retryButton = screen.getByRole('button');
    expect(retryButton).toBeDefined();
  });

  it('renders correlation ID when provided', () => {
    render(<ErrorPlaceholder message="エラー" correlationId="abc-123" />);
    expect(screen.getByText('abc-123')).toBeDefined();
  });

  it('does not render correlation ID when not provided', () => {
    const { container } = render(<ErrorPlaceholder message="エラー" />);
    expect(container.querySelector('code')).toBeNull();
  });
});
