import { test, expect, type Page } from '@playwright/test';
import { seedAdminAuth, seedEmployeeAuth, seedLeaderAuth } from '../support/auth';

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
    // stubAllApiCalls first so seedEmployeeAuth's /api/auth/refresh mock
    // (registered after, LIFO-highest priority) isn't shadowed by it.
    await stubAllApiCalls(page);
    await seedEmployeeAuth(page);

    await page.goto('/admin/shifts');
    await page.waitForURL('**/check-in');
  });

  test('EMPLOYEE accessing /leader/dashboard is redirected to /check-in', async ({ page }) => {
    await stubAllApiCalls(page);
    await seedEmployeeAuth(page);

    await page.goto('/leader/dashboard');
    await page.waitForURL('**/check-in');
  });

  test('ADMIN accessing /check-in is redirected to /admin/shifts', async ({ page }) => {
    await stubAllApiCalls(page);
    await seedAdminAuth(page);

    await page.goto('/check-in');
    await page.waitForURL('**/admin/shifts');
  });

  test('LEADER accessing /check-in is redirected to /leader/dashboard', async ({ page }) => {
    await stubAllApiCalls(page);
    await seedLeaderAuth(page);

    await page.goto('/check-in');
    await page.waitForURL('**/leader/dashboard');
  });

  test('LEADER accessing /admin/shifts is redirected to /leader/dashboard', async ({ page }) => {
    await stubAllApiCalls(page);
    await seedLeaderAuth(page);

    await page.goto('/admin/shifts');
    await page.waitForURL('**/leader/dashboard');
  });
});

test.describe('Protected Routes — mustChangePassword Gate', () => {
  test('user with mustChangePassword accessing /check-in is redirected to /change-password', async ({ page }) => {
    await stubAllApiCalls(page);
    await seedEmployeeAuth(page, { mustChangePassword: true });

    await page.goto('/check-in');
    await page.waitForURL('**/change-password');
  });

  test('user with mustChangePassword accessing /history is redirected to /change-password', async ({ page }) => {
    await stubAllApiCalls(page);
    await seedEmployeeAuth(page, { mustChangePassword: true });

    await page.goto('/history');
    await page.waitForURL('**/change-password');
  });
});
