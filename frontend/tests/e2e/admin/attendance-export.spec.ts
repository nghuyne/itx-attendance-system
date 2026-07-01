import { test, expect, type Page } from '@playwright/test';

// ── helpers ───────────────────────────────────────────────────────────────

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

const MOCK_RECORDS = [
  {
    id: 'rec-1', employeeId: 'emp-1', employeeName: 'Nguyen Van A',
    shiftId: 'shift-1', shiftName: 'Ca Sáng', date: '2026-06-15',
    checkInTime: '2026-06-15T01:05:00', checkOutTime: '2026-06-15T10:00:00',
    checkInPhotoUrl: null, checkOutPhotoUrl: null,
    attendanceStatus: 'ON_TIME', approvalSubStatus: 'APPROVED',
    isAdminOverride: false, version: 0, createdAt: '2026-06-15T01:05:00',
  },
];

const MOCK_PAGE = { content: MOCK_RECORDS, totalElements: 1, totalPages: 1, size: 20, number: 0 };

const MOCK_EMPLOYEES = [
  { id: 'emp-1', username: 'emp1', fullName: 'Nguyen Van A' },
  { id: 'emp-2', username: 'emp2', fullName: 'Tran Thi B' },
];

// ── Excel Export & Employee Filter (Story 7.2) ──────────────────────────────

test.describe('Admin — Xuất báo cáo Excel & Lọc nhân viên (Story 7.2)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.route('**/api/admin/employees**', route =>
      route.fulfill({ status: 200, json: MOCK_EMPLOYEES })
    );
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
  });

  // ── Employee filter dropdown ─────────────────────────────────────────────

  test('dropdown nhân viên hiển thị "Tất cả nhân viên" và danh sách nhân viên', async ({ page }) => {
    await page.goto('/admin/attendance');

    const select = page.locator('#employee');
    await expect(select).toContainText('Tất cả nhân viên');
    await expect(select).toContainText('Nguyen Van A');
    await expect(select).toContainText('Tran Thi B');
  });

  test('chọn nhân viên gửi đúng employeeId trong request', async ({ page }) => {
    let requestedUrl = '';
    await page.route('**/api/admin/attendance?**', async (route) => {
      requestedUrl = route.request().url();
      await route.fulfill({ status: 200, json: MOCK_PAGE });
    });
    await page.goto('/admin/attendance');

    await page.locator('#employee').selectOption({ value: 'emp-1' });

    await expect.poll(() => requestedUrl).toContain('employeeId=emp-1');
  });

  // ── Export button ─────────────────────────────────────────────────────

  test('nút "Xuất Excel" hiển thị mặc định khi chưa export', async ({ page }) => {
    await page.goto('/admin/attendance');

    await expect(page.getByRole('button', { name: 'Xuất Excel' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Xuất Excel' })).toBeEnabled();
  });

  test('bấm "Xuất Excel" gọi đúng endpoint export với from/to', async ({ page }) => {
    let requestedUrl = '';
    await page.route('**/api/admin/attendance/export**', async (route) => {
      requestedUrl = route.request().url();
      await route.fulfill({
        status: 200,
        headers: {
          'content-type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          'content-disposition': 'attachment; filename="attendance-2026-06-01-2026-06-30.xlsx"',
        },
        body: Buffer.from('fake-xlsx-content'),
      });
    });
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Xuất Excel' }).click();

    await expect.poll(() => requestedUrl).toContain('/admin/attendance/export');
  });

  test('export thành công → toast "Đang tải xuống báo cáo..."', async ({ page }) => {
    await page.route('**/api/admin/attendance/export**', route =>
      route.fulfill({
        status: 200,
        headers: {
          'content-type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          'content-disposition': 'attachment; filename="attendance.xlsx"',
        },
        body: Buffer.from('fake-xlsx-content'),
      })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Xuất Excel' }).click();

    await expect(page.getByText('Đang tải xuống báo cáo...')).toBeVisible();
  });

  test('export thất bại → toast lỗi', async ({ page }) => {
    await page.route('**/api/admin/attendance/export**', route =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'EXPORT_FAILED', message: 'Không thể tạo file Excel' }),
      })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Xuất Excel' }).click();

    await expect(page.getByText('Không thể tạo file Excel')).toBeVisible();
  });

  test('export với nhân viên đã chọn gửi đúng employeeId trong request export', async ({ page }) => {
    let requestedUrl = '';
    await page.route('**/api/admin/attendance/export**', async (route) => {
      requestedUrl = route.request().url();
      await route.fulfill({
        status: 200,
        headers: {
          'content-type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          'content-disposition': 'attachment; filename="attendance.xlsx"',
        },
        body: Buffer.from('fake-xlsx-content'),
      });
    });
    await page.goto('/admin/attendance');

    await page.locator('#employee').selectOption({ value: 'emp-1' });
    await page.getByRole('button', { name: 'Xuất Excel' }).click();

    await expect.poll(() => requestedUrl).toContain('employeeId=emp-1');
  });
});
