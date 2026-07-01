import { test, expect } from '@playwright/test';

// ── No token param ────────────────────────────────────────────────────────

test.describe('Reset Password Page — no token', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/reset-password');
  });

  test('shows invalid link message when token param is absent', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'ITX Attendance' })).toBeVisible();
    await expect(page.getByText('Link không hợp lệ.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Quay lại đăng nhập' })).toBeVisible();
  });

  test('navigates to /login via back button from invalid-link state', async ({ page }) => {
    await page.getByRole('button', { name: 'Quay lại đăng nhập' }).click();
    await page.waitForURL('**/login');
  });
});

// ── With valid token param ────────────────────────────────────────────────

test.describe('Reset Password Page — with token', () => {
  const TOKEN = 'valid-reset-token-abc123';

  test.beforeEach(async ({ page }) => {
    await page.goto(`/reset-password?token=${TOKEN}`);
  });

  test('renders reset password form with all required fields', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'ITX Attendance' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Đặt lại mật khẩu' })).toBeVisible();
    await expect(page.locator('#newPassword')).toBeVisible();
    await expect(page.locator('#confirmPassword')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Đặt lại mật khẩu' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Quay lại đăng nhập' })).toBeVisible();
  });

  test('shows error when new password is shorter than 8 characters', async ({ page }) => {
    await page.locator('#newPassword').fill('short');
    await page.locator('#confirmPassword').fill('short');
    await page.getByRole('button', { name: 'Đặt lại mật khẩu' }).click();

    await expect(
      page.getByRole('alert').filter({ hasText: 'Tối thiểu 8 ký tự' })
    ).toBeVisible();
  });

  test('shows error when passwords do not match', async ({ page }) => {
    await page.locator('#newPassword').fill('newPassword1');
    await page.locator('#confirmPassword').fill('differentPassword');
    await page.getByRole('button', { name: 'Đặt lại mật khẩu' }).click();

    await expect(
      page.getByRole('alert').filter({ hasText: 'Mật khẩu xác nhận không khớp' })
    ).toBeVisible();
  });

  test('success: shows toast and redirects to /login', async ({ page }) => {
    await page.route('**/api/auth/reset-password', (route) =>
      route.fulfill({ status: 200, body: '' })
    );

    await page.locator('#newPassword').fill('myNewPassword1');
    await page.locator('#confirmPassword').fill('myNewPassword1');
    await page.getByRole('button', { name: 'Đặt lại mật khẩu' }).click();

    await expect(page.getByText('Đặt lại mật khẩu thành công')).toBeVisible();
    await page.waitForURL('**/login');
  });

  test('TOKEN_EXPIRED: shows expiry error message', async ({ page }) => {
    await page.route('**/api/auth/reset-password', (route) =>
      route.fulfill({ status: 400, json: { error: 'TOKEN_EXPIRED', message: 'Token expired' } })
    );

    await page.locator('#newPassword').fill('myNewPassword1');
    await page.locator('#confirmPassword').fill('myNewPassword1');
    await page.getByRole('button', { name: 'Đặt lại mật khẩu' }).click();

    await expect(
      page.getByText('Link đã hết hạn. Vui lòng yêu cầu lại.')
    ).toBeVisible();
  });

  test('TOKEN_USED: shows already-used error message', async ({ page }) => {
    await page.route('**/api/auth/reset-password', (route) =>
      route.fulfill({ status: 400, json: { error: 'TOKEN_USED', message: 'Token used' } })
    );

    await page.locator('#newPassword').fill('myNewPassword1');
    await page.locator('#confirmPassword').fill('myNewPassword1');
    await page.getByRole('button', { name: 'Đặt lại mật khẩu' }).click();

    await expect(
      page.getByText('Link đã được sử dụng. Vui lòng yêu cầu lại.')
    ).toBeVisible();
  });

  test('TOKEN_INVALID: shows invalid token message', async ({ page }) => {
    await page.route('**/api/auth/reset-password', (route) =>
      route.fulfill({ status: 400, json: { error: 'TOKEN_INVALID', message: 'Invalid token' } })
    );

    await page.locator('#newPassword').fill('myNewPassword1');
    await page.locator('#confirmPassword').fill('myNewPassword1');
    await page.getByRole('button', { name: 'Đặt lại mật khẩu' }).click();

    await expect(page.getByText('Link không hợp lệ.')).toBeVisible();
  });

  test('navigates back to /login via back button', async ({ page }) => {
    await page.getByRole('button', { name: 'Quay lại đăng nhập' }).click();
    await page.waitForURL('**/login');
  });
});
