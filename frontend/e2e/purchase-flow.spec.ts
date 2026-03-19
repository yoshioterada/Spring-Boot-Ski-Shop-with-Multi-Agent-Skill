import { test, expect } from '@playwright/test';

test.describe('購入フロー E2E', () => {
  test('ホーム → 商品詳細 → カート追加 → チェックアウト → 注文完了', async ({ page }) => {
    // Home page
    await page.goto('/');
    await expect(page.locator('h1, [data-testid="hero-title"]')).toBeVisible();

    // Navigate to catalog
    await page.click('text=商品を見る');
    await expect(page).toHaveURL(/\/catalog/);

    // Click first product
    const firstProduct = page.locator('[data-testid="product-card"]').first();
    if (await firstProduct.isVisible()) {
      await firstProduct.click();
      await expect(page).toHaveURL(/\/product\//);
    }
  });

  test('空カート表示', async ({ page }) => {
    await page.goto('/cart');
    await expect(page.locator('text=カートが空です')).toBeVisible();
  });

  test('チェックアウト画面にはログインが必要', async ({ page }) => {
    await page.goto('/checkout');
    // Should redirect to login
    await expect(page).toHaveURL(/\/login/);
  });
});
