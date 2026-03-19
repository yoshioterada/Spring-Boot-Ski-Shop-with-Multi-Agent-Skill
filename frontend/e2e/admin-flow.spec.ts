import { test, expect } from '@playwright/test';

test.describe('管理画面 E2E', () => {
  test('管理画面にはADMINロールが必要', async ({ page }) => {
    await page.goto('/admin/dashboard');
    // Should redirect to login
    await expect(page).toHaveURL(/\/login/);
  });

  test('管理画面ダッシュボードの構造確認', async ({ page }) => {
    // This test would need an authenticated admin session
    // For now, verify redirect behavior
    await page.goto('/admin/products');
    await expect(page).toHaveURL(/\/login/);
  });
});
