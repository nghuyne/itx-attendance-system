import { test, expect, type Page } from '@playwright/test';

interface AuthUser {
  id: string;
  username: string;
  fullName: string;
  role: 'ADMIN' | 'LEADER' | 'EMPLOYEE';
  mustChangePassword: boolean;
}

async function seedAuth(page: Page, user: AuthUser) {
  await page.addInitScript(
    (storage: { key: string; value: unknown }) => {
      localStorage.setItem(storage.key, JSON.stringify(storage.value));
    },
    {
      key: 'itx-auth',
      value: { state: { user, isAuthenticated: true }, version: 0 },
    }
  );
}

async function stubAllApiCalls(page: Page) {
  // Abort non-auth API calls so page data doesn't load but auth redirects still fire
  await page.route('**/api/**', route => {
    const url = route.request().url();
    if (url.includes('/api/auth/')) {
      route.continue();
    } else {
      route.abort();
    }
  });
}

const USERS: Record<string, AuthUser> = {
  employee: { id: 'emp-id', username: 'emp1', fullName: 'Employee One', role: 'EMPLOYEE', mustChangePassword: false },
  admin: { id: 'admin-id', username: 'admin', fullName: 'System Administrator', role: 'ADMIN', mustChangePassword: false },
  leader: { id: 'leader-id', username: 'leader1', fullName: 'Leader One', role: 'LEADER', mustChangePassword: false },
  employeeMustChange: { id: 'emp-id', username: 'emp1', fullName: 'Employee One', role: 'EMPLOYEE', mustChangePassword: true },
};

test.describe('Protected Routes — Unauthenticated Redirects', () => {
  test('navigating to /check-in redirects to /login', async ({ page }) => {
    await page.goto('/check-in');
    await page.waitForURL('**/login');
  });

  test('navigating to /admin/shifts redirects to /login', async ({ page }) => {
    await page.goto('/admin/shifts');
    await page.waitForURL('**/login');
  });

  test('navigating to /leader/dashboard redirects to /login', async ({ page }) => {
    await page.goto('/leader/dashboard');
    await page.waitForURL('**/login');
  });

  test('/login is publicly accessible', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('#username')).toBeVisible();
  });

  test('/forgot-password is publicly accessible', async ({ page }) => {
    await page.goto('/forgot-password');
    await expect(page.locator('#email')).toBeVisible();
  });
});

test.describe('Protected Routes — Role-Based Redirects', () => {
  test('EMPLOYEE accessing /admin/shifts is redirected to /check-in', async ({ page }) => {
    await seedAuth(page, USERS.employee);
    await stubAllApiCalls(page);

    await page.goto('/admin/shifts');
    await page.waitForURL('**/check-in');
  });

  test('EMPLOYEE accessing /leader/dashboard is redirected to /check-in', async ({ page }) => {
    await seedAuth(page, USERS.employee);
    await stubAllApiCalls(page);

    await page.goto('/leader/dashboard');
    await page.waitForURL('**/check-in');
  });

  test('ADMIN accessing /check-in is redirected to /admin/shifts', async ({ page }) => {
    await seedAuth(page, USERS.admin);
    await stubAllApiCalls(page);

    await page.goto('/check-in');
    await page.waitForURL('**/admin/shifts');
  });

  test('LEADER accessing /check-in is redirected to /leader/dashboard', async ({ page }) => {
    await seedAuth(page, USERS.leader);
    await stubAllApiCalls(page);

    await page.goto('/check-in');
    await page.waitForURL('**/leader/dashboard');
  });
});

test.describe('Protected Routes — mustChangePassword Gate', () => {
  test('user with mustChangePassword accessing /check-in is redirected to /change-password', async ({ page }) => {
    await seedAuth(page, USERS.employeeMustChange);
    await stubAllApiCalls(page);

    await page.goto('/check-in');
    await page.waitForURL('**/change-password');
  });

  test('user with mustChangePassword accessing /history is redirected to /change-password', async ({ page }) => {
    await seedAuth(page, USERS.employeeMustChange);
    await stubAllApiCalls(page);

    await page.goto('/history');
    await page.waitForURL('**/change-password');
  });
});
