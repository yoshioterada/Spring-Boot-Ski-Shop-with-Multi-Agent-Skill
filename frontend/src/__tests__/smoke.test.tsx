import { describe, expect, it } from 'vitest';

describe('Smoke test', () => {
  it('should pass basic assertion', () => {
    expect(true).toBe(true);
  });

  it('should have correct environment', () => {
    expect(typeof window).toBe('object');
  });
});
