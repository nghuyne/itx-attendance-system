import { test, expect, type Page } from '@playwright/test';
import { seedEmployeeAuth as seedEmployeeAuthBase } from '../support/auth';

// ── helpers ───────────────────────────────────────────────────────────────

async function seedEmployeeAuth(page: Page) {
  await seedEmployeeAuthBase(page, { fullName: 'Employee One' });
}

type ToastType = 'success' | 'error' | 'info' | 'warning';

async function triggerToast(page: Page, type: ToastType, message: string, duration?: number) {
  // main.tsx populates __uiStore via an async import; wait until it's ready
  await page.waitForFunction(() => '__uiStore' in window);
  await page.evaluate(
    ({ type, message, duration }) => {
      type StoreHook = {
        getState: () => {
          showToast: (p: { type: string; message: string; duration?: number }) => void;
        };
      };
      const store = (window as Record<string, StoreHook>).__uiStore;
      store.getState().showToast({
        type,
        message,
        ...(duration !== undefined ? { duration } : {}),
      });
    },
    { type, message, duration }
  );
}

// ── Toast ─────────────────────────────────────────────────────────────────

test.describe('Toast Component', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('error toast renders with role, aria-live, and message via login failure', async ({ page }) => {
    await page.route('**/api/auth/login', (route) =>
      route.fulfill({ status: 401, json: { error: 'INVALID_CREDENTIALS', message: 'Invalid' } })
    );
    await page.route('**/api/auth/refresh', (route) =>
      route.fulfill({ status: 200, json: { accessToken: 'mock-token' } })
    );

    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('wrongpassword');
    await page.getByRole('button', { name: 'Đăng nhập' }).click();

    const toast = page.locator('[role="status"][aria-live="polite"]');
    await expect(toast).toBeVisible();
    await expect(toast).toHaveAttribute('aria-atomic', 'true');
    await expect(toast).toContainText('Tên đăng nhập hoặc mật khẩu không đúng');
    await expect(toast).toHaveClass(/bg-red-50/);
  });

  test('success toast renders with green styling', async ({ page }) => {
    await triggerToast(page, 'success', 'Lưu thành công!');

    const toast = page.locator('[role="status"]').filter({ hasText: 'Lưu thành công!' });
    await expect(toast).toBeVisible();
    await expect(toast).toHaveClass(/bg-green-50/);
  });

  test('warning toast renders with amber styling', async ({ page }) => {
    await triggerToast(page, 'warning', 'Cảnh báo quan trọng');

    const toast = page.locator('[role="status"]').filter({ hasText: 'Cảnh báo quan trọng' });
    await expect(toast).toBeVisible();
    await expect(toast).toHaveClass(/bg-amber-50/);
  });

  test('info toast renders with blue styling', async ({ page }) => {
    await triggerToast(page, 'info', 'Thông tin hệ thống');

    const toast = page.locator('[role="status"]').filter({ hasText: 'Thông tin hệ thống' });
    await expect(toast).toBeVisible();
    await expect(toast).toHaveClass(/bg-blue-50/);
  });

  test('toast auto-dismisses after 4 seconds', async ({ page }) => {
    await page.clock.install();
    await page.goto('/login');

    await triggerToast(page, 'info', 'Sẽ tự đóng sau 4 giây');

    const toast = page.locator('[role="status"]').filter({ hasText: 'Sẽ tự đóng sau 4 giây' });
    await expect(toast).toBeVisible();

    await page.clock.runFor(4100);
    await expect(toast).not.toBeVisible();
  });

  test('toast can be manually dismissed via close button', async ({ page }) => {
    await triggerToast(page, 'success', 'Nhấn × để đóng');

    const toast = page.locator('[role="status"]').filter({ hasText: 'Nhấn × để đóng' });
    await expect(toast).toBeVisible();

    await toast.getByRole('button', { name: 'Đóng thông báo' }).click();
    await expect(toast).not.toBeVisible();
  });

  test('toast container is always mounted with fixed top-right positioning', async ({ page }) => {
    const container = page.getByLabel('Thông báo hệ thống');
    await expect(container).toBeAttached();
    await expect(container).toHaveClass(/fixed/);
    await expect(container).toHaveClass(/top-4/);
    await expect(container).toHaveClass(/z-50/);
    await expect(container).toHaveClass(/sm:right-4/);
  });

  test('multiple toasts can coexist and be dismissed independently', async ({ page }) => {
    await triggerToast(page, 'success', 'Toast A');
    await triggerToast(page, 'error', 'Toast B');

    await expect(page.locator('[role="status"]').filter({ hasText: 'Toast A' })).toBeVisible();
    await expect(page.locator('[role="status"]').filter({ hasText: 'Toast B' })).toBeVisible();

    await page.locator('[role="status"]').filter({ hasText: 'Toast A' })
      .getByRole('button', { name: 'Đóng thông báo' }).click();

    await expect(page.locator('[role="status"]').filter({ hasText: 'Toast A' })).not.toBeVisible();
    await expect(page.locator('[role="status"]').filter({ hasText: 'Toast B' })).toBeVisible();
  });
});

// ── LoadingSpinner ────────────────────────────────────────────────────────

test.describe('LoadingSpinner Component', () => {
  test('renders inside login button while API call is in-flight', async ({ page }) => {
    let releaseRoute!: () => void;
    const routeBlocked = new Promise<void>((resolve) => {
      releaseRoute = resolve;
    });

    await page.route('**/api/auth/login', async (route) => {
      await routeBlocked;
      route.abort();
    });

    await page.goto('/login');
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('admin123');
    await page.getByRole('button', { name: 'Đăng nhập' }).click();

    const spinner = page.locator('[role="status"][aria-label="Đang tải..."]');
    await expect(spinner).toBeVisible();
    await expect(spinner).toHaveClass(/animate-spin/);
    await expect(page.getByText('Đang đăng nhập...')).toBeVisible();
    await expect(
      page.locator('button[type="submit"]').filter({ hasText: 'Đang đăng nhập...' })
    ).toBeDisabled();

    releaseRoute();
  });
});

// ── SkeletonCard ──────────────────────────────────────────────────────────

test.describe('SkeletonCard Component', () => {
  test('renders while attendance history is loading', async ({ page }) => {
    let releaseAttendanceRoute!: () => void;
    const attendanceRouteBlocked = new Promise<void>((resolve) => {
      releaseAttendanceRoute = resolve;
    });

    // Broad catch-all (lower priority — registered first in LIFO)
    await page.route('**/api/**', (route) => route.abort());

    // Specific attendance handler (higher priority — registered last in LIFO)
    await page.route('**/api/attendance**', async (route) => {
      await attendanceRouteBlocked;
      route.abort();
    });

    // seedEmployeeAuth's own /api/auth/refresh mock must be registered last
    // so it isn't shadowed by the broad catch-all above (Playwright matches
    // routes LIFO — most-recently-registered wins).
    await seedEmployeeAuth(page);

    await page.goto('/history');

    const skeleton = page.locator('[aria-busy="true"][aria-label="Đang tải nội dung..."]');
    await expect(skeleton.first()).toBeVisible();

    releaseAttendanceRoute();
  });

  test('skeleton renders header placeholder and line placeholders', async ({ page }) => {
    let releaseRoute!: () => void;
    const blocked = new Promise<void>((resolve) => { releaseRoute = resolve; });

    await page.route('**/api/**', (route) => route.abort());
    await page.route('**/api/attendance**', async (route) => {
      await blocked;
      route.abort();
    });

    // Registered last so its /api/auth/refresh mock isn't shadowed by the
    // broad catch-all above (Playwright matches routes LIFO).
    await seedEmployeeAuth(page);

    await page.goto('/history');

    const skeleton = page.locator('[aria-busy="true"]').first();
    await expect(skeleton).toHaveClass(/animate-pulse/);
    await expect(skeleton).toHaveClass(/rounded-lg/);

    releaseRoute();
  });
});

// ── OfflineBanner ─────────────────────────────────────────────────────────

test.describe('OfflineBanner Component', () => {
  test('appears when browser goes offline', async ({ page }) => {
    await page.goto('/login');
    await page.evaluate(() => window.dispatchEvent(new Event('offline')));

    const banner = page.locator('[role="alert"]').filter({ hasText: 'Mất kết nối Internet' });
    await expect(banner).toBeVisible();
    await expect(banner).toHaveAttribute('aria-live', 'assertive');
    await expect(banner).toContainText('Hệ thống sẽ đồng bộ khi online.');
  });

  test('disappears when connection is restored', async ({ page }) => {
    await page.goto('/login');
    await page.evaluate(() => window.dispatchEvent(new Event('offline')));

    const banner = page.locator('[role="alert"]').filter({ hasText: 'Mất kết nối Internet' });
    await expect(banner).toBeVisible();

    await page.evaluate(() => window.dispatchEvent(new Event('online')));
    await expect(banner).not.toBeVisible();
  });

  test('is not visible on initial page load when online', async ({ page }) => {
    await page.goto('/login');
    const banner = page.locator('[role="alert"]').filter({ hasText: 'Mất kết nối Internet' });
    await expect(banner).not.toBeVisible();
  });
});

// ── ErrorBoundary ─────────────────────────────────────────────────────────

test.describe('ErrorBoundary Component', () => {
  async function triggerErrorBoundary(page: Page, message: string) {
    await page.evaluate((errorMessage: string) => {
      const rootEl = document.getElementById('root');
      if (!rootEl) throw new Error('No root element');

      // React 18 createRoot stores the HostRoot fiber under __reactContainer$* on the
      // container element; older versions use __reactFiber$*.
      const fiberKey = Object.keys(rootEl).find(
        (k) => k.startsWith('__reactContainer$') || k.startsWith('__reactFiber$')
      );
      if (!fiberKey) throw new Error('No React fiber found on root element');

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      function findErrorBoundary(node: any): any {
        if (!node) return null;
        if (node.type && node.type.name === 'ErrorBoundary' && node.stateNode) {
          return node.stateNode;
        }
        return findErrorBoundary(node.child) ?? findErrorBoundary(node.sibling);
      }

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const instance = findErrorBoundary((rootEl as any)[fiberKey]);
      if (!instance) throw new Error('ErrorBoundary instance not found in fiber tree');

      instance.setState({ hasError: true, error: new Error(errorMessage) });
    }, message);
  }

  test('shows default fallback UI when a child component throws', async ({ page }) => {
    await page.goto('/login');
    await triggerErrorBoundary(page, 'Simulated render error');

    const fallback = page.locator('[aria-live="assertive"]');
    await expect(fallback).toBeVisible();
    await expect(page.getByText('Đã xảy ra lỗi không mong muốn')).toBeVisible();
    await expect(page.getByText('Simulated render error')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Tải lại trang' })).toBeVisible();
  });

  test('fallback replaces app content — no blank screen', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('#username')).toBeVisible();

    await triggerErrorBoundary(page, 'Component crash test');

    await expect(page.locator('#username')).not.toBeVisible();
    await expect(page.getByText('Đã xảy ra lỗi không mong muốn')).toBeVisible();
  });
});
