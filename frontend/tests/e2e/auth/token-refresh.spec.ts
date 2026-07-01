import { test, expect, type Page } from '@playwright/test';

async function seedEmployeeAuth(page: Page) {
  await page.addInitScript(
    (storage: { key: string; value: unknown }) => {
      localStorage.setItem(storage.key, JSON.stringify(storage.value));
    },
    {
      key: 'itx-auth',
      value: {
        state: {
          user: {
            id: 'emp-id',
            username: 'emp1',
            fullName: 'Employee One',
            role: 'EMPLOYEE',
            mustChangePassword: false,
          },
          isAuthenticated: true,
        },
        version: 0,
      },
    }
  );
}

const EMPTY_PAGE: unknown = {
  content: [],
  totalElements: 0,
  totalPages: 1,
  size: 10,
  number: 0,
};

// ── Token Refresh Interceptor ─────────────────────────────────────────────

test.describe('Token Refresh Interceptor', () => {
  test('retries original request after 401 + successful refresh — no redirect to /login', async ({ page }) => {
    await seedEmployeeAuth(page);

    // Catch-all: abort everything except the routes defined below
    await page.route('**/api/**', (route) => route.abort());

    // attendance/history: first call → 401, retry → 200
    let historyCallCount = 0;
    await page.route('**/api/attendance/history**', (route) => {
      historyCallCount++;
      if (historyCallCount === 1) {
        route.fulfill({ status: 401, json: { error: 'UNAUTHORIZED', message: 'Token expired' } });
      } else {
        route.fulfill({ status: 200, json: EMPTY_PAGE });
      }
    });

    // refresh endpoint: success — returns a new access token
    await page.route('**/api/auth/refresh', (route) =>
      route.fulfill({ status: 200, json: { accessToken: 'refreshed-access-token' } })
    );

    await page.goto('/history');

    // The interceptor should retry and the page should load without redirecting to /login
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page).toHaveURL(/\/history/);

    // At least 2 calls: first (401) + interceptor retry (200); StrictMode may add more
    expect(historyCallCount).toBeGreaterThanOrEqual(2);
  });

  test('retried request carries the new access token in Authorization header', async ({ page }) => {
    await seedEmployeeAuth(page);

    await page.route('**/api/**', (route) => route.abort());

    let secondRequestAuth: string | null = null;
    let callCount = 0;
    await page.route('**/api/attendance/history**', (route) => {
      callCount++;
      if (callCount === 1) {
        route.fulfill({ status: 401, json: { error: 'UNAUTHORIZED' } });
      } else {
        secondRequestAuth = route.request().headers()['authorization'] ?? null;
        route.fulfill({ status: 200, json: EMPTY_PAGE });
      }
    });

    await page.route('**/api/auth/refresh', (route) =>
      route.fulfill({ status: 200, json: { accessToken: 'refreshed-access-token' } })
    );

    await page.goto('/history');

    // Wait until the retry completes
    await expect(page).toHaveURL(/\/history/);
    await page.waitForFunction(() => document.querySelector('[aria-busy="true"]') === null, {
      timeout: 5000,
    }).catch(() => null); // skeleton may already be gone

    expect(secondRequestAuth).toBe('Bearer refreshed-access-token');
  });

  test('refresh failure (500) clears auth and redirects to /login', async ({ page }) => {
    await seedEmployeeAuth(page);

    await page.route('**/api/**', (route) => route.abort());

    // attendance/history always returns 401
    await page.route('**/api/attendance/history**', (route) =>
      route.fulfill({ status: 401, json: { error: 'UNAUTHORIZED' } })
    );

    // refresh endpoint fails with a server error (not 401 — avoids deadlock in interceptor)
    await page.route('**/api/auth/refresh', (route) =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_SERVER_ERROR' } })
    );

    await page.goto('/history');

    // clearAuth() is called → window.location.replace('/login') fires
    await page.waitForURL('**/login', { timeout: 8000 });

    // The login form is rendered, confirming auth was cleared and the user was logged out
    await expect(page.locator('#username')).toBeVisible();
  });
});
