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

const MOCK_LOGS = [
  {
    id: 1, adminId: 'admin-1', adminName: 'Audit Admin',
    targetTable: 'attendance_records', targetId: 'a1b2c3d4-e5f6-4711-9999-000000000001',
    fieldChanged: 'check_in_time', oldValue: '2026-06-15T01:00:00', newValue: '2026-06-15T01:30:00',
    reason: 'Chỉnh giờ vào cho đúng thực tế', createdAt: '2026-06-15T02:00:00Z',
  },
  {
    id: 2, adminId: 'admin-2', adminName: 'Second Admin',
    targetTable: 'attendance_records', targetId: 'rec-2',
    fieldChanged: 'attendance_status', oldValue: null, newValue: 'EXCUSED',
    reason: 'Nhân viên nghỉ có phép, admin cập nhật trạng thái', createdAt: '2026-06-14T03:00:00Z',
  },
];

const MOCK_PAGE = { content: MOCK_LOGS, totalElements: 2, totalPages: 1, size: 50, number: 0 };
const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, size: 50, number: 0 };

const MOCK_ADMINS = [
  { id: 'admin-1', username: 'audit_admin', fullName: 'Audit Admin' },
  { id: 'admin-2', username: 'audit_admin2', fullName: 'Second Admin' },
];

// ── Immutable Audit Log Viewer (Story 5.2) ──────────────────────────────────

test.describe('Admin — Audit Log Viewer (Story 5.2)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.route('**/api/admin/admins', route =>
      route.fulfill({ status: 200, json: MOCK_ADMINS })
    );
  });

  // ── List ──────────────────────────────────────────────────────────────

  test('hiển thị bảng audit log với đủ 8 cột dữ liệu', async ({ page }) => {
    await page.route('**/api/admin/audit-logs**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/audit');

    await expect(page.getByRole('heading', { name: 'Audit Logs' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Audit Admin' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Second Admin' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'check_in_time' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'attendance_status' })).toBeVisible();
    await expect(page.getByText('Chỉnh giờ vào cho đúng thực tế')).toBeVisible();
  });

  test('Record ID dài được rút gọn với dấu ellipsis', async ({ page }) => {
    await page.route('**/api/admin/audit-logs**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/audit');

    await expect(page.getByText('a1b2c3d4…')).toBeVisible();
    await expect(page.getByText('rec-2', { exact: true })).toBeVisible();
  });

  test('giá trị cũ null hiển thị dấu gạch ngang', async ({ page }) => {
    await page.route('**/api/admin/audit-logs**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/audit');

    const row = page.getByRole('row', { name: /attendance_status/ });
    await expect(row.getByRole('cell', { name: '—', exact: true })).toBeVisible();
  });

  test('hiển thị trạng thái trống khi không có audit log nào', async ({ page }) => {
    await page.route('**/api/admin/audit-logs**', route =>
      route.fulfill({ status: 200, json: EMPTY_PAGE })
    );
    await page.goto('/admin/audit');

    await expect(page.getByText('Không có audit log nào trong khoảng thời gian này.')).toBeVisible();
  });

  test('hiển thị trạng thái lỗi khi API thất bại', async ({ page }) => {
    await page.route('**/api/admin/audit-logs**', route =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } })
    );
    await page.goto('/admin/audit');

    await expect(page.getByText('Không thể tải audit logs. Vui lòng thử lại.')).toBeVisible();
  });

  // ── Filters ───────────────────────────────────────────────────────────

  test('dropdown Admin load từ API và lọc đúng adminId trong query', async ({ page }) => {
    let requestedUrl = '';
    await page.route('**/api/admin/audit-logs**', async (route) => {
      requestedUrl = route.request().url();
      await route.fulfill({ status: 200, json: MOCK_PAGE });
    });
    await page.goto('/admin/audit');

    await expect(page.locator('#adminFilter option')).toHaveCount(3);
    await page.locator('#adminFilter').selectOption({ value: 'admin-2' });

    await expect.poll(() => requestedUrl).toContain('adminId=admin-2');
  });

  test('dropdown Bảng lọc đúng targetTable trong query', async ({ page }) => {
    let requestedUrl = '';
    await page.route('**/api/admin/audit-logs**', async (route) => {
      requestedUrl = route.request().url();
      await route.fulfill({ status: 200, json: MOCK_PAGE });
    });
    await page.goto('/admin/audit');

    await page.locator('#tableFilter').selectOption({ value: 'attendance_records' });

    await expect.poll(() => requestedUrl).toContain('targetTable=attendance_records');
  });

  test('lỗi tải danh sách admin hiển thị thông báo, không chặn bảng chính', async ({ page }) => {
    await page.unroute('**/api/admin/admins');
    await page.route('**/api/admin/admins', route =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } })
    );
    await page.route('**/api/admin/audit-logs**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/audit');

    await expect(page.getByText('Không tải được danh sách admin')).toBeVisible();
    await expect(page.getByText('Audit Admin')).toBeVisible();
  });

  test('mặc định khoảng ngày là 7 ngày trước đến hôm nay', async ({ page }) => {
    await page.route('**/api/admin/audit-logs**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/audit');

    const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
    const fromDefault = sevenDaysAgo.toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });

    await expect(page.locator('#to')).toHaveValue(today);
    await expect(page.locator('#from')).toHaveValue(fromDefault);
  });

  // ── Pagination ────────────────────────────────────────────────────────

  test('phân trang hiển thị đúng "Trang X/Y" và disable nút phù hợp', async ({ page }) => {
    const page2 = { ...MOCK_PAGE, number: 0, totalPages: 2 };
    await page.route('**/api/admin/audit-logs**', route =>
      route.fulfill({ status: 200, json: page2 })
    );
    await page.goto('/admin/audit');

    await expect(page.getByText('Trang 1/2')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Trước' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Sau' })).toBeEnabled();
  });

  test('nhấn "Sau" gửi đúng page=1 trong request', async ({ page }) => {
    let requestedUrl = '';
    await page.route('**/api/admin/audit-logs**', async (route) => {
      requestedUrl = route.request().url();
      await route.fulfill({ status: 200, json: { ...MOCK_PAGE, totalPages: 2 } });
    });
    await page.goto('/admin/audit');

    await page.getByRole('button', { name: 'Sau' }).click();

    await expect.poll(() => requestedUrl).toContain('page=1');
  });

  // ── AC-2: Immutability (UI) ──────────────────────────────────────────────

  test('bảng audit log hoàn toàn read-only — không có nút sửa/xóa', async ({ page }) => {
    await page.route('**/api/admin/audit-logs**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/audit');

    await expect(page.getByRole('button', { name: /sửa|edit/i })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /xóa|delete/i })).toHaveCount(0);
  });
});
