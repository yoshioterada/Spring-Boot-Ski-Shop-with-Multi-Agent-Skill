import { describe, expect, it } from 'vitest';

import { t } from '@/lib/i18n';

describe('i18n t() helper', () => {
  it('returns value for a simple key', () => {
    expect(t('shared.nav.home')).toBe('ホーム');
  });

  it('returns value for a nested key', () => {
    expect(t('shared.nav.login')).toBe('ログイン');
  });

  it('returns the key itself when not found', () => {
    expect(t('nonexistent.key')).toBe('nonexistent.key');
  });

  it('returns value for admin sidebar keys', () => {
    expect(t('admin.sidebar.dashboard')).toBe('ダッシュボード');
    expect(t('admin.sidebar.products')).toBe('商品');
  });

  it('returns value for footer keys', () => {
    expect(t('shared.footer.shop')).toBe('ショップ');
    expect(t('shared.footer.support')).toBe('サポート');
  });

  it('interpolates parameters', () => {
    const result = t('shared.pagination.showing', { total: '100', from: '1', to: '20' });
    expect(result).toContain('100');
    expect(result).toContain('1');
    expect(result).toContain('20');
  });
});
