import { test, expect, type Page } from '@playwright/test';
import { seedEmployeeAuth } from '../support/auth';

// ── helpers ───────────────────────────────────────────────────────────────

// LeaveRequestModal rejects a start date in the past, so submission tests
// need dates relative to "today" rather than a fixed calendar date.
const futureDate = (daysFromNow: number) => {
  const d = new Date();
  d.setDate(d.getDate() + daysFromNow);
  return d.toISOString().split('T')[0];
};

function makeBalances(overrides: { annual?: Partial<Record<string, unknown>>; sick?: Partial<Record<string, unknown>> } = {}) {
  return [
    { id: 1, employeeId: 'emp-id', year: 2026, leaveType: 'ANNUAL', totalDays: 12, usedDays: 0, ...overrides.annual },
    { id: 2, employeeId: 'emp-id', year: 2026, leaveType: 'SICK', totalDays: 5, usedDays: 0, ...overrides.sick },
  ];
}

function makeLeaveRequest(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    requestCategory: 'LEAVE',
    employeeId: 'emp-id',
    attendanceRecordId: null,
    attendanceDate: null,
    requestType: null,
    proposedCheckoutTime: null,
    checkInTime: null,
    checkOutTime: null,
    reason: 'Về quê thăm gia đình dịp lễ',
    status: 'PENDING',
    reviewedBy: null,
    reviewReason: null,
    createdAt: '2026-07-01T02:00:00',
    updatedAt: '2026-07-01T02:00:00',
    leaveType: 'ANNUAL',
    startDate: '2026-07-10',
    endDate: '2026-07-12',
    totalDays: 3,
    plannedDate: null,
    plannedOtHours: null,
    ...overrides,
  };
}

// ── EmployeeRequestsPage — Leave balance & history (Story 6.1) ─────────────

test.describe('Employee — Quỹ phép & Lịch sử nghỉ phép (Story 6.1)', () => {
  test.beforeEach(async ({ page }) => {
    await seedEmployeeAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.route('**/api/requests/me', route =>
      route.fulfill({ status: 200, json: [] })
    );
  });

  test('hiển thị LeaveBalanceCard với số ngày Phép năm và Phép ốm', async ({ page }) => {
    await page.route('**/api/requests/leave-balance', route =>
      route.fulfill({ status: 200, json: makeBalances() })
    );
    await page.goto('/requests');

    await expect(page.getByText('Phép năm')).toBeVisible();
    await expect(page.getByText('0 / 12 ngày đã dùng')).toBeVisible();
    await expect(page.getByText('Phép ốm')).toBeVisible();
    await expect(page.getByText('0 / 5 ngày đã dùng')).toBeVisible();
  });

  test('yêu cầu nghỉ phép xuất hiện trong lịch sử với badge "NGHỈ PHÉP"', async ({ page }) => {
    await page.route('**/api/requests/leave-balance', route =>
      route.fulfill({ status: 200, json: makeBalances() })
    );
    await page.unroute('**/api/requests/me');
    await page.route('**/api/requests/me', route =>
      route.fulfill({ status: 200, json: [makeLeaveRequest('leave-1')] })
    );
    await page.goto('/requests');

    await expect(page.getByText('NGHỈ PHÉP', { exact: true })).toBeVisible();
    await expect(page.getByText('Phép năm · 3 ngày')).toBeVisible();
  });

  test('nút "Xin nghỉ phép" mở LeaveRequestModal', async ({ page }) => {
    await page.route('**/api/requests/leave-balance', route =>
      route.fulfill({ status: 200, json: makeBalances() })
    );
    await page.goto('/requests');

    await page.getByRole('button', { name: 'Xin nghỉ phép' }).click();

    await expect(page.getByRole('heading', { name: 'Xin nghỉ phép' })).toBeVisible();
  });
});

// ── LeaveRequestModal (Story 6.1) ───────────────────────────────────────────

test.describe('Employee — LeaveRequestModal (Story 6.1)', () => {
  test.beforeEach(async ({ page }) => {
    await seedEmployeeAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.route('**/api/requests/me', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.route('**/api/requests/leave-balance', route =>
      route.fulfill({ status: 200, json: makeBalances() })
    );
  });

  async function openModal(page: Page) {
    await page.goto('/requests');
    await page.getByRole('button', { name: 'Xin nghỉ phép' }).click();
    await expect(page.getByRole('heading', { name: 'Xin nghỉ phép' })).toBeVisible();
  }

  test('hiển thị dropdown loại phép, ngày bắt đầu/kết thúc và lý do', async ({ page }) => {
    await openModal(page);

    await expect(page.getByRole('dialog').getByText('Loại phép')).toBeVisible();
    await expect(page.getByRole('dialog').getByText('Ngày bắt đầu')).toBeVisible();
    await expect(page.getByRole('dialog').getByText('Ngày kết thúc')).toBeVisible();
    await expect(page.getByRole('dialog').getByText('Lý do', { exact: true })).toBeVisible();
  });

  test('hiển thị "Còn lại: X / Y ngày" theo loại phép đã chọn', async ({ page }) => {
    await openModal(page);

    await expect(page.getByText('Còn lại:')).toBeVisible();
    await expect(page.getByText('12 / 12 ngày')).toBeVisible();
  });

  test('chọn ngày bắt đầu/kết thúc → hiển thị "Số ngày nghỉ: X ngày"', async ({ page }) => {
    await openModal(page);

    await page.locator('input[name="startDate"]').fill('2026-07-10');
    await page.locator('input[name="endDate"]').fill('2026-07-12');

    await expect(page.getByText('Số ngày nghỉ:')).toBeVisible();
    await expect(page.getByText('3 ngày', { exact: true })).toBeVisible();
  });

  test('ngày kết thúc trước ngày bắt đầu → lỗi validation', async ({ page }) => {
    await openModal(page);

    await page.locator('input[name="startDate"]').fill('2026-07-12');
    await page.locator('input[name="endDate"]').fill('2026-07-10');
    await page.locator('textarea[name="reason"]').fill('Lý do nghỉ phép hợp lệ đủ dài');

    await expect(page.getByText('Ngày kết thúc phải sau hoặc bằng ngày bắt đầu')).toBeVisible();
  });

  test('lý do < 10 ký tự → nút Gửi bị disabled', async ({ page }) => {
    await openModal(page);

    await page.locator('input[name="startDate"]').fill('2026-07-10');
    await page.locator('input[name="endDate"]').fill('2026-07-12');
    await page.locator('textarea[name="reason"]').fill('Quá ngắn');

    await expect(page.getByRole('button', { name: 'Gửi đơn nghỉ phép' })).toBeDisabled();
  });

  test('số ngày nghỉ vượt quỹ phép còn lại → cảnh báo "không đủ"', async ({ page }) => {
    await page.unroute('**/api/requests/leave-balance');
    await page.route('**/api/requests/leave-balance', route =>
      route.fulfill({ status: 200, json: makeBalances({ annual: { usedDays: 11, totalDays: 12 } }) })
    );
    await openModal(page);

    await page.locator('input[name="startDate"]').fill('2026-07-10');
    await page.locator('input[name="endDate"]').fill('2026-07-14');

    await expect(page.getByText('Số ngày nghỉ còn lại không đủ')).toBeVisible();
  });

  test('nút Đóng đóng modal', async ({ page }) => {
    await openModal(page);

    await page.getByRole('button', { name: 'Đóng' }).click();

    await expect(page.getByRole('heading', { name: 'Xin nghỉ phép' })).not.toBeVisible();
  });

  test('phím Escape đóng modal', async ({ page }) => {
    await openModal(page);

    await page.keyboard.press('Escape');

    await expect(page.getByRole('heading', { name: 'Xin nghỉ phép' })).not.toBeVisible();
  });

  test('gửi thành công → toast success + modal đóng', async ({ page }) => {
    await page.route('**/api/requests/leave', route =>
      route.fulfill({
        status: 201,
        json: {
          id: 'leave-1', employeeId: 'emp-id', leaveType: 'ANNUAL',
          startDate: '2026-07-10', endDate: '2026-07-12', totalDays: 3,
          reason: 'Lý do nghỉ phép hợp lệ đủ dài để vượt validation',
          status: 'PENDING', approverId: null, rejectionReason: null,
          createdAt: '2026-07-01T02:00:00', updatedAt: '2026-07-01T02:00:00',
        },
      })
    );
    await openModal(page);

    await page.locator('input[name="startDate"]').fill(futureDate(1));
    await page.locator('input[name="endDate"]').fill(futureDate(3));
    await page.locator('textarea[name="reason"]').fill('Lý do nghỉ phép hợp lệ đủ dài để vượt validation');
    await page.getByRole('button', { name: 'Gửi đơn nghỉ phép' }).click();

    await expect(page.getByText('Gửi đơn nghỉ phép thành công')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Xin nghỉ phép' })).not.toBeVisible();
  });

  test('lỗi INSUFFICIENT_LEAVE_BALANCE → toast lỗi cụ thể', async ({ page }) => {
    await page.route('**/api/requests/leave', route =>
      route.fulfill({ status: 400, json: { error: 'INSUFFICIENT_LEAVE_BALANCE', message: 'Quỹ phép không đủ' } })
    );
    await openModal(page);

    await page.locator('input[name="startDate"]').fill(futureDate(1));
    await page.locator('input[name="endDate"]').fill(futureDate(3));
    await page.locator('textarea[name="reason"]').fill('Lý do nghỉ phép hợp lệ đủ dài để vượt validation');
    await page.getByRole('button', { name: 'Gửi đơn nghỉ phép' }).click();

    await expect(page.getByText('Quỹ phép không đủ')).toBeVisible();
  });

  test('lỗi LEAVE_DATE_CONFLICT → toast lỗi cụ thể', async ({ page }) => {
    await page.route('**/api/requests/leave', route =>
      route.fulfill({ status: 409, json: { error: 'LEAVE_DATE_CONFLICT', message: 'Ngày nghỉ trùng với đơn đã gửi' } })
    );
    await openModal(page);

    await page.locator('input[name="startDate"]').fill(futureDate(1));
    await page.locator('input[name="endDate"]').fill(futureDate(3));
    await page.locator('textarea[name="reason"]').fill('Lý do nghỉ phép hợp lệ đủ dài để vượt validation');
    await page.getByRole('button', { name: 'Gửi đơn nghỉ phép' }).click();

    await expect(page.getByText('Ngày nghỉ trùng với đơn đã gửi')).toBeVisible();
  });

  test('dialog có role="dialog" và aria-modal="true"', async ({ page }) => {
    await openModal(page);

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog).toHaveAttribute('aria-modal', 'true');
  });
});
