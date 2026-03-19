import { test, expect } from '@playwright/test';

test.describe('レスポンシブ E2E', () => {
  test('モバイル（375px）でホームページが表示される', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/');
    await expect(page.locator('body')).toBeVisible();
    // Mobile hamburger menu should be visible
    await expect(page.locator('[aria-label*="メニュー"], button:has(svg)')).toBeVisible();
  });

  test('タブレット（768px）でホームページが表示される', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/');
    await expect(page.locator('body')).toBeVisible();
  });

  test('デスクトップ（1280px）でホームページが表示される', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');
    await expect(page.locator('body')).toBeVisible();
  });

  test('モバイルで検索ページが表示される', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/search');
    await expect(page.locator('body')).toBeVisible();
  });

  test('モバイルでカタログページが表示される', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/catalog');
    await expect(page.locator('body')).toBeVisible();
  });
});
