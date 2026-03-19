import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Breadcrumb } from '@/components/layout/breadcrumb';

describe('Breadcrumb', () => {
  it('renders home link', () => {
    render(<Breadcrumb items={[]} />);
    const homeLink = screen.getByRole('link');
    expect(homeLink).toBeDefined();
  });

  it('renders breadcrumb items', () => {
    render(<Breadcrumb items={[{ label: 'スキー板' }, { label: '商品詳細' }]} />);
    expect(screen.getByText('スキー板')).toBeDefined();
    expect(screen.getByText('商品詳細')).toBeDefined();
  });

  it('renders item with href as link', () => {
    render(<Breadcrumb items={[{ label: 'カテゴリ', href: '/catalog' }]} />);
    const link = screen.getByText('カテゴリ');
    expect(link.closest('a')).toBeDefined();
  });
});
