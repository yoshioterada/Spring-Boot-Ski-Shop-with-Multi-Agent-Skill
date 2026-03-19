import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Pagination } from '@/components/common/pagination';

describe('Pagination', () => {
  const defaultProps = {
    currentPage: 0,
    totalPages: 5,
    totalElements: 100,
    pageSize: 20,
    onPageChange: () => {},
  };

  it('renders showing info text', () => {
    render(<Pagination {...defaultProps} />);
    const text = screen.getByText(/100/);
    expect(text).toBeDefined();
  });

  it('renders navigation buttons', () => {
    render(<Pagination {...defaultProps} />);
    const buttons = screen.getAllByRole('button');
    expect(buttons.length).toBeGreaterThanOrEqual(2);
  });

  it('disables previous buttons on first page', () => {
    render(<Pagination {...defaultProps} currentPage={0} />);
    const buttons = screen.getAllByRole('button');
    const firstButton = buttons[0];
    expect(firstButton.hasAttribute('disabled') || firstButton.getAttribute('aria-disabled')).toBeTruthy();
  });

  it('disables next buttons on last page', () => {
    render(<Pagination {...defaultProps} currentPage={4} />);
    const buttons = screen.getAllByRole('button');
    const lastButton = buttons[buttons.length - 1];
    expect(lastButton.hasAttribute('disabled') || lastButton.getAttribute('aria-disabled')).toBeTruthy();
  });
});
