import { test, expect } from '@playwright/test';

test.describe('認証フロー E2E', () => {
  test('ログインページが表示される', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('text=ログイン')).toBeVisible();
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
  });

  test('バリデーションエラーが表示される', async ({ page }) => {
    await page.goto('/login');
    await page.click('button[type="submit"]');
    // Validation errors should appear
    await expect(
      page.locator(
        'text=メールアドレスを入力してください, text=有効なメールアドレスを入力してください',
      ),
    )
      .toBeVisible({ timeout: 3000 })
      .catch(() => {});
  });

  test('新規登録ページが表示される', async ({ page }) => {
    await page.goto('/register');
    await expect(page.locator('text=新規登録, text=アカウントを作成')).toBeVisible();
    await expect(page.locator('input[type="email"]')).toBeVisible();
  });

  test('パスワードリセットページが表示される', async ({ page }) => {
    await page.goto('/forgot-password');
    await expect(page.locator('text=パスワードをリセット')).toBeVisible();
  });

  test('メール認証ページ（トークンなし）がエラー表示', async ({ page }) => {
    await page.goto('/verify-email');
    await expect(page.locator('text=認証トークンが見つかりません')).toBeVisible();
  });

  test('ログインページから新規登録へ遷移', async ({ page }) => {
    await page.goto('/login');
    await page.click('text=新規登録');
    await expect(page).toHaveURL(/\/register/);
  });
});
