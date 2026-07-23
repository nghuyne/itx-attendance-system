import { test, expect, type Page } from '@playwright/test';
import { seedAdminAuth, seedEmployeeAuth } from '../support/auth';

async function stubNonAuthApi(page: Page) {
  await page.route('**/api/**', (route) => {
    const url = route.request().url();
    if (url.includes('/api/auth/')) route.continue();
    else route.abort();
  });
}

// ── Voluntary change (mustChangePassword = false) ─────────────────────────

test.describe('Change Password Page — voluntary change', () => {
  test.beforeEach(async ({ page }) => {
    // Register the broad catch-all first so the /api/auth/refresh mock
    // registered by seedEmployeeAuth (added after, LIFO-highest priority)
    // isn't shadowed by it.
    await stubNonAuthApi(page);
    await seedEmployeeAuth(page, { mustChangePassword: false });
    await page.goto('/change-password');
  });

  test('renders form with all required fields', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'ITX Attendance' })).toBeVisible();
    await expect(page.locator('#oldPassword')).toBeVisible();
    await expect(page.locator('#newPassword')).toBeVisible();
    await expect(page.locator('#confirmPassword')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Đổi mật khẩu' })).toBeVisible();
  });

  test('shows back button when not forced to change password', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Quay lại' })).toBeVisible();
    await expect(page.getByText('Đổi mật khẩu tài khoản')).toBeVisible();
  });

  test('shows validation errors for all empty fields on submit', async ({ page }) => {
    await page.getByRole('button', { name: 'Đổi mật khẩu' }).click();

    await expect(
      page.getByRole('alert').filter({ hasText: 'Vui lòng nhập mật khẩu hiện tại' })
    ).toBeVisible();
    await expect(
      page.getByRole('alert').filter({ hasText: 'Mật khẩu mới phải có ít nhất 6 ký tự' })
    ).toBeVisible();
    await expect(
      page.getByRole('alert').filter({ hasText: 'Vui lòng xác nhận mật khẩu mới' })
    ).toBeVisible();
  });

  test('shows error for new password shorter than 6 characters', async ({ page }) => {
    await page.locator('#oldPassword').fill('currentPass');
    await page.locator('#newPassword').fill('abc');
    await page.locator('#confirmPassword').fill('abc');
    await page.getByRole('button', { name: 'Đổi mật khẩu' }).click();

    await expect(
      page.getByRole('alert').filter({ hasText: 'Mật khẩu mới phải có ít nhất 6 ký tự' })
    ).toBeVisible();
  });

  test('shows mismatch error when new and confirm passwords differ', async ({ page }) => {
    await page.locator('#oldPassword').fill('currentPass');
    await page.locator('#newPassword').fill('newPass123');
    await page.locator('#confirmPassword').fill('differentPass');
    await page.getByRole('button', { name: 'Đổi mật khẩu' }).click();

    await expect(
      page.getByRole('alert').filter({ hasText: 'Mật khẩu xác nhận không khớp' })
    ).toBeVisible();
  });

  test('success: shows toast and redirects EMPLOYEE to /check-in', async ({ page }) => {
    await page.route('**/api/auth/change-password', (route) =>
      route.fulfill({ status: 200, body: '' })
    );

    await page.locator('#oldPassword').fill('currentPass');
    await page.locator('#newPassword').fill('newPass123');
    await page.locator('#confirmPassword').fill('newPass123');
    await page.getByRole('button', { name: 'Đổi mật khẩu' }).click();

    await expect(page.getByText('Đổi mật khẩu thành công')).toBeVisible();
    await page.waitForURL('**/check-in');
  });

  test('shows error toast when old password is incorrect', async ({ page }) => {
    await page.route('**/api/auth/change-password', (route) =>
      route.fulfill({ status: 400, json: { error: 'INVALID_OLD_PASSWORD', message: 'Wrong password' } })
    );

    await page.locator('#oldPassword').fill('wrongOldPass');
    await page.locator('#newPassword').fill('newPass123');
    await page.locator('#confirmPassword').fill('newPass123');
    await page.getByRole('button', { name: 'Đổi mật khẩu' }).click();

    await expect(
      page.getByText('Mật khẩu hiện tại không đúng. Vui lòng thử lại.')
    ).toBeVisible();
  });
});

// ── Forced change (mustChangePassword = true) ─────────────────────────────

test.describe('Change Password Page — forced change', () => {
  test.beforeEach(async ({ page }) => {
    await stubNonAuthApi(page);
    await seedEmployeeAuth(page, { mustChangePassword: true });
    await page.goto('/change-password');
  });

  test('shows forced-change notice and hides back button', async ({ page }) => {
    await expect(
      page.getByText('Tài khoản của bạn cần đổi mật khẩu trước khi tiếp tục.')
    ).toBeVisible();
    await expect(page.getByRole('button', { name: 'Quay lại' })).not.toBeVisible();
  });

  test('success: redirects to /check-in after forced password change', async ({ page }) => {
    await page.route('**/api/auth/change-password', (route) =>
      route.fulfill({ status: 200, body: '' })
    );

    await page.locator('#oldPassword').fill('tempPass');
    await page.locator('#newPassword').fill('myNewPass123');
    await page.locator('#confirmPassword').fill('myNewPass123');
    await page.getByRole('button', { name: 'Đổi mật khẩu' }).click();

    await expect(page.getByText('Đổi mật khẩu thành công')).toBeVisible();
    await page.waitForURL('**/check-in');
  });
});

// ── ADMIN role redirect ───────────────────────────────────────────────────

test.describe('Change Password Page — ADMIN role', () => {
  test('success: redirects ADMIN to /admin/shifts', async ({ page }) => {
    await stubNonAuthApi(page);
    await seedAdminAuth(page, { mustChangePassword: false });
    await page.route('**/api/auth/change-password', (route) =>
      route.fulfill({ status: 200, body: '' })
    );

    await page.goto('/change-password');

    await page.locator('#oldPassword').fill('currentPass');
    await page.locator('#newPassword').fill('newPass123');
    await page.locator('#confirmPassword').fill('newPass123');
    await page.getByRole('button', { name: 'Đổi mật khẩu' }).click();

    await page.waitForURL('**/admin/shifts');
  });
});
