import { test, expect, type Page } from '@playwright/test';

// ── helpers ───────────────────────────────────────────────────────────────
// Tests run via leader dashboard — LeaderLayout includes Header with NotificationBell

async function seedLeaderAuth(page: Page) {
  await page.addInitScript(
    (storage: { key: string; value: unknown }) => {
      localStorage.setItem(storage.key, JSON.stringify(storage.value));
    },
    {
      key: 'itx-auth',
      value: {
        state: {
          user: { id: 'leader-id', username: 'leader1', fullName: 'Tran Thi B', role: 'LEADER', mustChangePassword: false },
          isAuthenticated: true,
        },
        version: 0,
      },
    }
  );
}

function makeNotification(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    recipientId: 'leader-id',
    type: 'EXCEPTION_REQUEST',
    referenceId: 'req-1',
    message: 'Nhân viên Nguyen Van A gửi yêu cầu ngoại lệ',
    isRead: false,
    createdAt: '2026-06-30T02:00:00',
    ...overrides,
  };
}

async function setupLeaderPage(page: Page) {
  await page.route('**/api/leader/team-roster**', route =>
    route.fulfill({ status: 200, json: [] })
  );
  await page.route('**/api/requests/pending', route =>
    route.fulfill({ status: 200, json: [] })
  );
}

// ── Notification Bell ─────────────────────────────────────────────────────

test.describe('NotificationBell (Story 4.2)', () => {
  test.beforeEach(async ({ page }) => {
    await seedLeaderAuth(page);
    await setupLeaderPage(page);
  });

  test('chuông không hiển thị badge khi unreadCount = 0', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.goto('/leader/dashboard');

    const bell = page.getByRole('button', { name: /Thông báo/ });
    await expect(bell).toBeVisible();
    // No numeric badge
    await expect(bell.locator('span').filter({ hasText: /^\d/ })).not.toBeVisible();
  });

  test('chuông hiển thị badge với số đếm khi có thông báo chưa đọc', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({
        status: 200,
        json: { notifications: [makeNotification('n-1'), makeNotification('n-2')], unreadCount: 2 },
      })
    );
    await page.goto('/leader/dashboard');

    const badge = page.getByRole('button', { name: /Thông báo/ }).locator('span');
    await expect(badge).toBeVisible();
    await expect(badge).toContainText('2');
  });

  test('chuông hiển thị "9+" khi unreadCount > 9', async ({ page }) => {
    const notifications = Array.from({ length: 10 }, (_, i) => makeNotification(`n-${i}`));
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications, unreadCount: 10 } })
    );
    await page.goto('/leader/dashboard');

    const badge = page.getByRole('button', { name: /Thông báo/ }).locator('span');
    await expect(badge).toContainText('9+');
  });

  test('aria-label phản ánh số thông báo chưa đọc', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({
        status: 200,
        json: { notifications: [makeNotification('n-1')], unreadCount: 1 },
      })
    );
    await page.goto('/leader/dashboard');

    await expect(page.getByRole('button', { name: /Thông báo/ }))
      .toHaveAttribute('aria-label', 'Thông báo, 1 chưa đọc');
  });

  test('aria-label không có số khi unreadCount = 0', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.goto('/leader/dashboard');

    await expect(page.getByRole('button', { name: 'Thông báo' }))
      .toHaveAttribute('aria-label', 'Thông báo');
  });

  test('click chuông mở NotificationPanel', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();

    await expect(page.getByRole('dialog', { name: 'Bảng thông báo' })).toBeVisible();
  });
});

// ── Notification Panel ────────────────────────────────────────────────────

test.describe('NotificationPanel (Story 4.2)', () => {
  test.beforeEach(async ({ page }) => {
    await seedLeaderAuth(page);
    await setupLeaderPage(page);
  });

  test('panel có role="dialog" và aria-label="Bảng thông báo"', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();

    const panel = page.getByRole('dialog', { name: 'Bảng thông báo' });
    await expect(panel).toBeVisible();
    await expect(panel).toHaveAttribute('aria-modal', 'true');
  });

  test('trạng thái rỗng hiển thị "Không có thông báo mới"', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();

    await expect(page.getByText('Không có thông báo mới')).toBeVisible();
  });

  test('hiển thị danh sách thông báo', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({
        status: 200,
        json: {
          notifications: [
            makeNotification('n-1', { message: 'Nhân viên Nguyen Van A gửi yêu cầu ngoại lệ' }),
            makeNotification('n-2', { message: 'Nhân viên Le Thi B gửi yêu cầu điều chỉnh', type: 'ADJUSTMENT_REQUEST' }),
          ],
          unreadCount: 2,
        },
      })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();

    await expect(page.getByText('Nhân viên Nguyen Van A gửi yêu cầu ngoại lệ')).toBeVisible();
    await expect(page.getByText('Nhân viên Le Thi B gửi yêu cầu điều chỉnh')).toBeVisible();
  });

  test('nút "Đánh dấu tất cả đã đọc" chỉ hiển thị khi unreadCount > 0', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({
        status: 200,
        json: { notifications: [makeNotification('n-1')], unreadCount: 1 },
      })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();

    await expect(page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' })).toBeVisible();
  });

  test('nút "Đánh dấu tất cả đã đọc" ẩn khi không có thông báo chưa đọc', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();

    await expect(page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' })).not.toBeVisible();
  });

  test('click "Đánh dấu tất cả đã đọc" gọi PUT /api/notifications/read-all', async ({ page }) => {
    let markAllReadCalled = false;
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({
        status: 200,
        json: { notifications: [makeNotification('n-1')], unreadCount: 1 },
      })
    );
    await page.route('**/api/notifications/read-all', route => {
      markAllReadCalled = true;
      return route.fulfill({ status: 200, json: { updatedCount: 1 } });
    });
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();
    await page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' }).click();

    expect(markAllReadCalled).toBe(true);
  });

  test('nút đóng panel có aria-label="Đóng bảng thông báo"', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();

    const closeBtn = page.getByRole('button', { name: 'Đóng bảng thông báo' });
    await expect(closeBtn).toBeVisible();
    await expect(closeBtn).toHaveAttribute('aria-label', 'Đóng bảng thông báo');
  });

  test('click nút đóng ✕ đóng panel', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();
    await expect(page.getByRole('dialog', { name: 'Bảng thông báo' })).toBeVisible();

    await page.getByRole('button', { name: 'Đóng bảng thông báo' }).click();
    await expect(page.getByRole('dialog', { name: 'Bảng thông báo' })).not.toBeVisible();
  });

  test('phím Escape đóng panel', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();
    await expect(page.getByRole('dialog', { name: 'Bảng thông báo' })).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog', { name: 'Bảng thông báo' })).not.toBeVisible();
  });

  test('click backdrop đóng panel', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();
    await expect(page.getByRole('dialog', { name: 'Bảng thông báo' })).toBeVisible();

    // Click the backdrop overlay (aria-hidden div behind the panel)
    await page.locator('[aria-hidden="true"].fixed.inset-0').click({ position: { x: 100, y: 300 } });
    await expect(page.getByRole('dialog', { name: 'Bảng thông báo' })).not.toBeVisible();
  });

  test('thông báo SUSPICIOUS_LOCATION có viền và nền amber', async ({ page }) => {
    const suspiciousNotif = makeNotification('n-suspicious', {
      type: 'SUSPICIOUS_LOCATION',
      message: 'Cảnh báo: Nguyen Van A check-in từ vị trí bất thường',
    });
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({
        status: 200,
        json: { notifications: [suspiciousNotif], unreadCount: 1 },
      })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();

    const listItem = page.locator('li').filter({ hasText: 'Cảnh báo: Nguyen Van A check-in từ vị trí bất thường' });
    await expect(listItem).toBeVisible();
    await expect(listItem).toHaveClass(/bg-amber-50/);
    await expect(listItem).toHaveClass(/border-l-amber-500/);
  });

  test('thông báo thường (EXCEPTION_REQUEST) không có nền amber', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({
        status: 200,
        json: { notifications: [makeNotification('n-1')], unreadCount: 1 },
      })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: /Thông báo/ }).click();

    const listItem = page.locator('li').filter({ hasText: 'Nhân viên Nguyen Van A gửi yêu cầu ngoại lệ' });
    await expect(listItem).not.toHaveClass(/bg-amber-50/);
  });
});

// ── Admin — Suspicious Location Notification (Story 8.2) ────────────────────

async function seedAdminAuth(page: Page) {
  await page.addInitScript(
    (storage: { key: string; value: unknown }) => {
      localStorage.setItem(storage.key, JSON.stringify(storage.value));
    },
    {
      key: 'itx-auth',
      value: {
        state: {
          user: { id: 'admin-id', username: 'admin', fullName: 'System Administrator', role: 'ADMIN', mustChangePassword: false },
          isAuthenticated: true,
        },
        version: 0,
      },
    }
  );
}

function makeSuspiciousNotification(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    recipientId: 'admin-id',
    type: 'SUSPICIOUS_LOCATION',
    referenceId: 'rec-1',
    message: '⚠ Cảnh báo: Nguyen Van A check-in lúc 09:15 từ vị trí bất thường (62.3km)',
    isRead: false,
    createdAt: '2026-06-30T02:15:00',
    ...overrides,
  };
}

test.describe('Admin — Suspicious Location Notification (Story 8.2)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/admin/employees**', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 } })
    );
  });

  test('icon cảnh báo ⚠️ hiển thị cho thông báo SUSPICIOUS_LOCATION', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({
        status: 200,
        json: { notifications: [makeSuspiciousNotification('n-susp')], unreadCount: 1 },
      })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: /Thông báo/ }).click();

    const listItem = page.locator('li').filter({ hasText: 'check-in lúc 09:15 từ vị trí bất thường' });
    await expect(listItem).toBeVisible();
    await expect(listItem).toContainText('⚠️');
  });

  test('click thông báo SUSPICIOUS_LOCATION điều hướng đến /admin/attendance và đánh dấu đã đọc', async ({ page }) => {
    let readCalled = false;
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({
        status: 200,
        json: { notifications: [makeSuspiciousNotification('n-susp')], unreadCount: 1 },
      })
    );
    await page.route('**/api/notifications/n-susp/read', route => {
      readCalled = true;
      route.fulfill({ status: 200, json: { ...makeSuspiciousNotification('n-susp'), isRead: true } });
    });
    // Start from a different admin page so the navigation assertion is meaningful.
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: /Thông báo/ }).click();
    await page.locator('li').filter({ hasText: 'check-in lúc 09:15 từ vị trí bất thường' }).click();

    await expect(page).toHaveURL(/\/admin\/attendance$/);
    expect(readCalled).toBe(true);
  });

  test('click thông báo SUSPICIOUS_LOCATION đóng NotificationPanel', async ({ page }) => {
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({
        status: 200,
        json: { notifications: [makeSuspiciousNotification('n-susp')], unreadCount: 1 },
      })
    );
    await page.route('**/api/notifications/n-susp/read', route =>
      route.fulfill({ status: 200, json: { ...makeSuspiciousNotification('n-susp'), isRead: true } })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: /Thông báo/ }).click();
    await expect(page.getByRole('dialog', { name: 'Bảng thông báo' })).toBeVisible();

    await page.locator('li').filter({ hasText: 'check-in lúc 09:15 từ vị trí bất thường' }).click();

    await expect(page.getByRole('dialog', { name: 'Bảng thông báo' })).not.toBeVisible();
  });
});
