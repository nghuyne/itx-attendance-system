import { test, expect } from '@playwright/test';
import { seedAdminAuth } from '../support/auth';

// ── helpers ───────────────────────────────────────────────────────────────

const MOCK_RECORDS = [
  {
    id: 'rec-1', employeeId: 'emp-1', employeeName: 'Nguyen Van A',
    shiftId: 'shift-1', shiftName: 'Ca Sáng', date: '2026-06-15',
    checkInTime: '2026-06-15T01:05:00', checkOutTime: '2026-06-15T10:00:00',
    checkInPhotoUrl: 'https://cdn.itx.local/photos/old.jpg', checkOutPhotoUrl: null,
    attendanceStatus: 'LATE_IN', approvalSubStatus: 'APPROVED',
    isAdminOverride: false, version: 0, createdAt: '2026-06-15T01:05:00',
  },
  {
    id: 'rec-2', employeeId: 'emp-2', employeeName: 'Tran Thi B',
    shiftId: 'shift-1', shiftName: 'Ca Sáng', date: '2026-06-15',
    checkInTime: null, checkOutTime: null,
    checkInPhotoUrl: null, checkOutPhotoUrl: null,
    attendanceStatus: 'ABSENT', approvalSubStatus: 'ADMIN_OVERRIDE',
    isAdminOverride: true, version: 1, createdAt: '2026-06-15T00:00:00',
  },
];

const MOCK_PAGE = { content: MOCK_RECORDS, totalElements: 2, totalPages: 1, size: 20, number: 0 };
const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 };

const MOCK_EMPLOYEES = [
  { id: 'emp-1', username: 'emp1', fullName: 'Nguyen Van A' },
  { id: 'emp-2', username: 'emp2', fullName: 'Tran Thi B' },
];

// ── Admin Manual Override of Attendance Records (Story 5.1) ────────────────

test.describe('Admin — Ghi đè bản ghi chấm công (Story 5.1)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.route('**/api/admin/employees**', route =>
      route.fulfill({ status: 200, json: MOCK_EMPLOYEES })
    );
  });

  // ── List ──────────────────────────────────────────────────────────────

  test('hiển thị danh sách chấm công với badge ADMIN_OVERRIDE', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await expect(page.getByRole('heading', { name: 'Chấm công' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Nguyen Van A', exact: true })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Tran Thi B', exact: true })).toBeVisible();
    await expect(page.getByText('ADMIN_OVERRIDE')).toBeVisible();
  });

  test('hiển thị trạng thái trống khi không có bản ghi', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: EMPTY_PAGE })
    );
    await page.goto('/admin/attendance');

    await expect(page.getByText('Không có bản ghi nào')).toBeVisible();
  });

  test('hiển thị trạng thái lỗi khi API thất bại', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } })
    );
    await page.goto('/admin/attendance');

    await expect(page.getByText('Không thể tải dữ liệu chấm công. Vui lòng thử lại.')).toBeVisible();
  });

  // ── Open modal / prefill ─────────────────────────────────────────────

  test('nút "Override" mở modal với dữ liệu bản ghi hiện có', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Override bản ghi của Nguyen Van A' }).click();

    await expect(page.getByRole('heading', { name: 'Override Bản Ghi Chấm Công' })).toBeVisible();
    await expect(page.locator('#checkInTime')).toHaveValue('2026-06-15T01:05');
    await expect(page.locator('#checkOutTime')).toHaveValue('2026-06-15T10:00');
  });

  // ── Reason validation ─────────────────────────────────────────────────

  test('nút "Lưu thay đổi" bị disabled khi chưa nhập lý do', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Override bản ghi của Nguyen Van A' }).click();

    await expect(page.getByRole('button', { name: 'Lưu thay đổi' })).toBeDisabled();
  });

  test('nút "Lưu thay đổi" vẫn disabled khi lý do dưới 10 ký tự', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Override bản ghi của Nguyen Van A' }).click();
    await page.locator('#auditReason').fill('quá ngắn');

    await expect(page.getByRole('button', { name: 'Lưu thay đổi' })).toBeDisabled();
  });

  test('nút "Lưu thay đổi" được bật khi lý do đủ 10 ký tự', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Override bản ghi của Nguyen Van A' }).click();
    await page.locator('#auditReason').fill('Chỉnh sửa lại giờ vào cho đúng thực tế');

    await expect(page.getByRole('button', { name: 'Lưu thay đổi' })).toBeEnabled();
  });

  // ── Submit success / error ────────────────────────────────────────────

  test('lưu override thành công → toast success', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', async (route) => {
      await route.fulfill({ status: 200, json: MOCK_PAGE });
    });
    await page.route('**/api/admin/attendance/rec-1/override', async (route) => {
      await route.fulfill({ status: 200, json: { ...MOCK_RECORDS[0], isAdminOverride: true } });
    });
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Override bản ghi của Nguyen Van A' }).click();
    await page.locator('#auditReason').fill('Chỉnh sửa lại giờ vào cho đúng thực tế');
    await page.getByRole('button', { name: 'Lưu thay đổi' }).click();

    await expect(page.getByText('Ghi đè bản ghi thành công')).toBeVisible();
  });

  test('modal đóng sau khi lưu thành công', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.route('**/api/admin/attendance/rec-1/override', async (route) => {
      await route.fulfill({ status: 200, json: { ...MOCK_RECORDS[0], isAdminOverride: true } });
    });
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Override bản ghi của Nguyen Van A' }).click();
    await page.locator('#auditReason').fill('Chỉnh sửa lại giờ vào cho đúng thực tế');
    await page.getByRole('button', { name: 'Lưu thay đổi' }).click();

    await expect(page.getByRole('heading', { name: 'Override Bản Ghi Chấm Công' })).not.toBeVisible();
  });

  test('API lỗi khi lưu override → toast lỗi', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.route('**/api/admin/attendance/rec-1/override', async (route) => {
      await route.fulfill({ status: 400, json: { error: 'AMBIGUOUS_OVERRIDE', message: 'Không thể đặt trạng thái thủ công khi đồng thời thay đổi thời gian' } });
    });
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Override bản ghi của Nguyen Van A' }).click();
    await page.locator('#auditReason').fill('Chỉnh sửa lại giờ vào cho đúng thực tế');
    await page.getByRole('button', { name: 'Lưu thay đổi' }).click();

    await expect(page.getByText('Có lỗi xảy ra, vui lòng thử lại')).toBeVisible();
  });

  // ── Close / Escape ────────────────────────────────────────────────────

  test('nút Hủy đóng modal không lưu', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Override bản ghi của Nguyen Van A' }).click();
    await expect(page.getByRole('heading', { name: 'Override Bản Ghi Chấm Công' })).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByRole('heading', { name: 'Override Bản Ghi Chấm Công' })).not.toBeVisible();
  });

  test('phím Escape đóng modal', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('button', { name: 'Override bản ghi của Nguyen Van A' }).click();
    await expect(page.getByRole('heading', { name: 'Override Bản Ghi Chấm Công' })).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(page.getByRole('heading', { name: 'Override Bản Ghi Chấm Công' })).not.toBeVisible();
  });

  // ── Filters ───────────────────────────────────────────────────────────

  test('lọc theo nhân viên gửi đúng employeeId trong request', async ({ page }) => {
    let requestedUrl = '';
    await page.route('**/api/admin/attendance?**', async (route) => {
      requestedUrl = route.request().url();
      await route.fulfill({ status: 200, json: MOCK_PAGE });
    });
    await page.goto('/admin/attendance');

    await page.locator('#employee').selectOption({ value: 'emp-1' });

    await expect.poll(() => requestedUrl).toContain('employeeId=emp-1');
  });

  test('lọc theo trạng thái hiển thị badge bộ lọc và có thể xóa', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('checkbox').first().check();
    await expect(page.getByText('Bộ lọc:')).toBeVisible();

    await page.getByRole('button', { name: 'Xóa bộ lọc' }).first().click();
    await expect(page.getByText('Bộ lọc:')).not.toBeVisible();
  });
});
