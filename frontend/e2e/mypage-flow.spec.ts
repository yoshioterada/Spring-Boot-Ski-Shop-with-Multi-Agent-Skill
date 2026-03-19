import { test, expect } from '@playwright/test';

test.describe('マイページ E2E', () => {
  test('マイページにはログインが必要', async ({ page }) => {
    await page.goto('/mypage');
    await expect(page).toHaveURL(/\/login/);
  });

  test('注文履歴ページにはログインが必要', async ({ page }) => {
    await page.goto('/mypage/orders');
    await expect(page).toHaveURL(/\/login/);
  });

  test('プロフィールページにはログインが必要', async ({ page }) => {
    await page.goto('/mypage/profile');
    await expect(page).toHaveURL(/\/login/);
  });
});
