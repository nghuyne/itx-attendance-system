import { test, expect } from '@playwright/test';

test.describe('Forgot Password Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/forgot-password');
  });

  test('renders the forgot password form', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'ITX Attendance' })).toBeVisible();
    await expect(page.getByText('Quên mật khẩu')).toBeVisible();
    await expect(page.locator('#email')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Gửi hướng dẫn' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Quay lại đăng nhập' })).toBeVisible();
  });

  test('shows validation error for empty email', async ({ page }) => {
    await page.getByRole('button', { name: 'Gửi hướng dẫn' }).click();
    await expect(page.getByRole('alert')).toBeVisible();
  });

  test('shows validation error for invalid email format', async ({ page }) => {
    await page.locator('#email').fill('not-an-email');
    await page.getByRole('button', { name: 'Gửi hướng dẫn' }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Email không hợp lệ' })).toBeVisible();
  });

  test('shows success state after submitting a valid email', async ({ page }) => {
    await page.route('**/api/auth/forgot-password', route =>
      route.fulfill({ status: 200, json: { message: 'Nếu email tồn tại, bạn sẽ nhận được hướng dẫn' } })
    );

    await page.locator('#email').fill('user@example.com');
    await page.getByRole('button', { name: 'Gửi hướng dẫn' }).click();

    await expect(
      page.getByText('Nếu email tồn tại trong hệ thống, bạn sẽ nhận được hướng dẫn qua email.')
    ).toBeVisible();
    await expect(page.getByRole('button', { name: 'Quay lại đăng nhập' })).toBeVisible();
  });

  test('navigates back to /login when clicking back button', async ({ page }) => {
    await page.getByRole('button', { name: 'Quay lại đăng nhập' }).click();
    await page.waitForURL('**/login');
  });
});
