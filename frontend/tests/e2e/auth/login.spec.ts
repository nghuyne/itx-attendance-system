import { test, expect } from '@playwright/test';

const MOCK = {
  admin: {
    accessToken: 'mock-token',
    user: { id: 'admin-id', username: 'admin', fullName: 'System Administrator', role: 'ADMIN', mustChangePassword: false },
  },
  employee: {
    accessToken: 'mock-token',
    user: { id: 'emp-id', username: 'emp1', fullName: 'Employee One', role: 'EMPLOYEE', mustChangePassword: false },
  },
  leader: {
    accessToken: 'mock-token',
    user: { id: 'leader-id', username: 'leader1', fullName: 'Leader One', role: 'LEADER', mustChangePassword: false },
  },
  mustChange: {
    accessToken: 'mock-token',
    user: { id: 'emp-id', username: 'emp1', fullName: 'Employee One', role: 'EMPLOYEE', mustChangePassword: true },
  },
};

test.describe('Login Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('renders login form with all required fields', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'ITX Attendance' })).toBeVisible();
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Đăng nhập' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Quên mật khẩu?' })).toBeVisible();
  });

  test('shows validation errors when submitting empty form', async ({ page }) => {
    await page.getByRole('button', { name: 'Đăng nhập' }).click();

    await expect(page.getByRole('alert').filter({ hasText: 'Vui lòng nhập tên đăng nhập' })).toBeVisible();
    await expect(page.getByRole('alert').filter({ hasText: 'Vui lòng nhập mật khẩu' })).toBeVisible();
  });

  test('shows error toast on invalid credentials', async ({ page }) => {
    await page.route('**/api/auth/login', route =>
      route.fulfill({ status: 401, json: { error: 'INVALID_CREDENTIALS', message: 'Invalid credentials' } })
    );
    // Refresh must succeed so the interceptor retries the login (with _retry=true),
    // then propagates the 401 error to the caller instead of redirecting to /login.
    await page.route('**/api/auth/refresh', route =>
      route.fulfill({ status: 200, json: { accessToken: 'mock-token' } })
    );

    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('wrongpassword');
    await page.getByRole('button', { name: 'Đăng nhập' }).click();

    await expect(page.getByText('Tên đăng nhập hoặc mật khẩu không đúng')).toBeVisible();
  });

  test('redirects ADMIN to /admin/shifts after successful login', async ({ page }) => {
    await page.route('**/api/auth/login', route => route.fulfill({ status: 200, json: MOCK.admin }));

    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('admin123');
    await page.getByRole('button', { name: 'Đăng nhập' }).click();

    await page.waitForURL('**/admin/shifts');
  });

  test('redirects EMPLOYEE to /check-in after successful login', async ({ page }) => {
    await page.route('**/api/auth/login', route => route.fulfill({ status: 200, json: MOCK.employee }));

    await page.locator('#username').fill('emp1');
    await page.locator('#password').fill('pass123');
    await page.getByRole('button', { name: 'Đăng nhập' }).click();

    await page.waitForURL('**/check-in');
  });

  test('redirects LEADER to /leader/dashboard after successful login', async ({ page }) => {
    await page.route('**/api/auth/login', route => route.fulfill({ status: 200, json: MOCK.leader }));

    await page.locator('#username').fill('leader1');
    await page.locator('#password').fill('pass123');
    await page.getByRole('button', { name: 'Đăng nhập' }).click();

    await page.waitForURL('**/leader/dashboard');
  });

  test('redirects to /change-password when mustChangePassword is true', async ({ page }) => {
    await page.route('**/api/auth/login', route => route.fulfill({ status: 200, json: MOCK.mustChange }));

    await page.locator('#username').fill('emp1');
    await page.locator('#password').fill('pass123');
    await page.getByRole('button', { name: 'Đăng nhập' }).click();

    await page.waitForURL('**/change-password');
  });

  test('navigates to forgot password page', async ({ page }) => {
    await page.getByRole('button', { name: 'Quên mật khẩu?' }).click();
    await page.waitForURL('**/forgot-password');
  });
});
