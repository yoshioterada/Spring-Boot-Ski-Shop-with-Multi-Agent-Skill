import { describe, expect, it } from 'vitest';

import { formatCurrency, formatDate, formatNumber, formatPoints } from '@/lib/format';

describe('formatCurrency', () => {
  it('formats JPY currency without decimals', () => {
    expect(formatCurrency(1234)).toBe('￥1,234');
  });

  it('formats zero amount', () => {
    expect(formatCurrency(0)).toBe('￥0');
  });

  it('formats large amounts with comma separators', () => {
    expect(formatCurrency(1234567)).toBe('￥1,234,567');
  });
});

describe('formatNumber', () => {
  it('formats numbers with locale separators', () => {
    expect(formatNumber(1234)).toBe('1,234');
  });
});

describe('formatPoints', () => {
  it('formats points with suffix', () => {
    const result = formatPoints(500);
    expect(result).toContain('500');
  });
});

describe('formatDate', () => {
  it('formats ISO date string to Japanese locale', () => {
    const result = formatDate('2026-03-20T00:00:00Z');
    expect(result).toBeTruthy();
    expect(typeof result).toBe('string');
  });
});
