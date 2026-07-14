import { test, expect } from '@playwright/test';
import { seedLeaderAuth } from '../support/auth';

// ── helpers ───────────────────────────────────────────────────────────────

function makeRosterItem(overrides: Record<string, unknown> = {}) {
  return {
    employeeId: 'emp-1',
    employeeName: 'Nguyen Van A',
    shiftId: 'shift-1',
    shiftName: 'Ca Sáng',
    shiftStartTime: '08:00',
    shiftEndTime: '17:00',
    attendanceStatus: 'ON_TIME',
    approvalSubStatus: null,
    checkInTime: '2026-06-30T01:10:00',
    checkOutTime: '2026-06-30T10:00:00',
    hasPendingRequest: false,
    pendingRequestId: null,
    pendingRequestCategory: null,
    ...overrides,
  };
}

function makeRequest(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    requestCategory: 'EXCEPTION',
    employeeId: 'emp-1',
    employeeName: 'Nguyen Van A',
    attendanceRecordId: 'rec-1',
    attendanceDate: '2026-06-30',
    requestType: 'LATE_IN',
    proposedCheckoutTime: null,
    checkInTime: '2026-06-30T01:30:00',
    checkOutTime: null,
    reason: 'Lý do xin ngoại lệ hợp lý và chi tiết',
    status: 'PENDING',
    reviewedBy: null,
    reviewReason: null,
    createdAt: '2026-06-30T02:00:00',
    updatedAt: '2026-06-30T02:00:00',
    leaveType: null,
    startDate: null,
    endDate: null,
    totalDays: null,
    plannedDate: null,
    plannedOtHours: null,
    ...overrides,
  };
}

// ── Leader Dashboard ──────────────────────────────────────────────────────

test.describe('Leader — Dashboard Nhóm (Story 4.3)', () => {
  test.beforeEach(async ({ page }) => {
    await seedLeaderAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [] })
    );
  });

  // ── Layout & navigation ────────────────────────────────────────────────

  test('hiển thị tiêu đề "Dashboard Nhóm"', async ({ page }) => {
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.goto('/leader/dashboard');

    await expect(page.getByRole('heading', { name: 'Dashboard Nhóm' })).toBeVisible();
  });

  test('hiển thị các nút điều hướng ngày (trước, hôm nay, sau)', async ({ page }) => {
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.goto('/leader/dashboard');

    await expect(page.getByRole('button', { name: '← Ngày trước' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Hôm nay' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Ngày sau →' })).toBeVisible();
  });

  test('hiển thị 4 thẻ tổng hợp (Đúng giờ, Đi muộn, Thiếu, Vắng)', async ({ page }) => {
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.goto('/leader/dashboard');

    await expect(page.getByText('Đúng giờ')).toBeVisible();
    await expect(page.getByText('Đi muộn')).toBeVisible();
    await expect(page.getByText('Thiếu')).toBeVisible();
    await expect(page.getByText('Vắng')).toBeVisible();
  });

  // ── Loading & empty states ────────────────────────────────────────────

  test('hiển thị skeleton loader khi đang tải danh sách', async ({ page }) => {
    await page.route('**/api/leader/team-roster**', async route => {
      await new Promise(resolve => setTimeout(resolve, 500));
      await route.fulfill({ status: 200, json: [] });
    });
    await page.goto('/leader/dashboard');

    await expect(page.locator('.animate-pulse').first()).toBeVisible();
  });

  test('trạng thái rỗng khi không có nhân viên trong nhóm', async ({ page }) => {
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.goto('/leader/dashboard');

    await expect(page.getByText('Không có nhân viên trong nhóm')).toBeVisible();
  });

  // ── Roster cards ───────────────────────────────────────────────────────

  test('hiển thị RosterCard với tên nhân viên và thông tin ca', async ({ page }) => {
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({ status: 200, json: [makeRosterItem()] })
    );
    await page.goto('/leader/dashboard');

    await expect(page.getByText('Nguyen Van A')).toBeVisible();
    await expect(page.getByText(/Ca Sáng: 08:00 - 17:00/)).toBeVisible();
  });

  test('thẻ tổng hợp đếm đúng số lượng nhân viên theo trạng thái', async ({ page }) => {
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({
        status: 200,
        json: [
          makeRosterItem({ employeeId: 'e1', employeeName: 'NV A', attendanceStatus: 'ON_TIME' }),
          makeRosterItem({ employeeId: 'e2', employeeName: 'NV B', attendanceStatus: 'ON_TIME' }),
          makeRosterItem({ employeeId: 'e3', employeeName: 'NV C', attendanceStatus: 'LATE_IN' }),
          makeRosterItem({ employeeId: 'e4', employeeName: 'NV D', attendanceStatus: 'ABSENT', checkInTime: null, checkOutTime: null }),
        ],
      })
    );
    await page.goto('/leader/dashboard');

    const summaryCards = page.locator('.grid.grid-cols-4 > div');
    await expect(summaryCards.nth(0).getByText('2')).toBeVisible();
    await expect(summaryCards.nth(1).getByText('1')).toBeVisible();
    await expect(summaryCards.nth(3).getByText('1')).toBeVisible();
  });

  test('RosterCard với yêu cầu đang chờ hiển thị chỉ thị cam và nút "Xem chi tiết"', async ({ page }) => {
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({
        status: 200,
        json: [makeRosterItem({
          hasPendingRequest: true,
          pendingRequestId: 'req-1',
          pendingRequestCategory: 'EXCEPTION',
        })],
      })
    );
    await page.goto('/leader/dashboard');

    await expect(page.getByTitle('Có yêu cầu đang chờ')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Xem chi tiết' })).toBeVisible();
  });

  // ── RequestDetailModal ────────────────────────────────────────────────

  test('click "Xem chi tiết" mở RequestDetailModal với thông tin yêu cầu', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({
        status: 200,
        json: [makeRosterItem({
          hasPendingRequest: true,
          pendingRequestId: 'req-1',
          pendingRequestCategory: 'EXCEPTION',
        })],
      })
    );
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: 'Xem chi tiết' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('Chi tiết yêu cầu')).toBeVisible();
    await expect(dialog.getByText('Nguyen Van A')).toBeVisible();
    await expect(dialog.getByText('Lý do xin ngoại lệ hợp lý và chi tiết')).toBeVisible();
  });

  test('duyệt yêu cầu → toast success + modal đóng', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({
        status: 200,
        json: [makeRosterItem({
          hasPendingRequest: true,
          pendingRequestId: 'req-1',
          pendingRequestCategory: 'EXCEPTION',
        })],
      })
    );
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.route('**/api/requests/*/approve', route =>
      route.fulfill({ status: 200, json: { ...request, status: 'APPROVED' } })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: 'Xem chi tiết' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();

    await page.getByRole('button', { name: 'Duyệt' }).click();

    await expect(page.getByText('Đã duyệt yêu cầu thành công')).toBeVisible();
    await expect(page.getByRole('dialog')).not.toBeVisible();
  });

  test('click "Từ chối" hiển thị RejectionReasonModal', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({
        status: 200,
        json: [makeRosterItem({
          hasPendingRequest: true,
          pendingRequestId: 'req-1',
          pendingRequestCategory: 'EXCEPTION',
        })],
      })
    );
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: 'Xem chi tiết' }).click();
    await page.getByRole('button', { name: 'Từ chối' }).click();

    await expect(page.getByText('Lý do từ chối')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Xác nhận từ chối' })).toBeVisible();
  });

  test('RejectionReasonModal: nút xác nhận disabled khi lý do trống', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({
        status: 200,
        json: [makeRosterItem({
          hasPendingRequest: true,
          pendingRequestId: 'req-1',
          pendingRequestCategory: 'EXCEPTION',
        })],
      })
    );
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: 'Xem chi tiết' }).click();
    await page.getByRole('button', { name: 'Từ chối' }).click();

    await expect(page.getByRole('button', { name: 'Xác nhận từ chối' })).toBeDisabled();
  });

  test('từ chối với lý do → toast success + modal đóng', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({
        status: 200,
        json: [makeRosterItem({
          hasPendingRequest: true,
          pendingRequestId: 'req-1',
          pendingRequestCategory: 'EXCEPTION',
        })],
      })
    );
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.route('**/api/requests/*/reject', route =>
      route.fulfill({ status: 200, json: { ...request, status: 'REJECTED', reviewReason: 'Không hợp lệ' } })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: 'Xem chi tiết' }).click();
    await page.getByRole('button', { name: 'Từ chối' }).click();
    await page.getByRole('textbox', { name: 'Lý do từ chối' }).fill('Lý do từ chối hợp lệ và chi tiết');
    await page.getByRole('button', { name: 'Xác nhận từ chối' }).click();

    await expect(page.getByText('Đã từ chối yêu cầu')).toBeVisible();
    await expect(page.getByRole('dialog')).not.toBeVisible();
  });

  test('RequestDetailModal đóng khi click nút ✕', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({
        status: 200,
        json: [makeRosterItem({
          hasPendingRequest: true,
          pendingRequestId: 'req-1',
          pendingRequestCategory: 'EXCEPTION',
        })],
      })
    );
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: 'Xem chi tiết' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();

    await page.getByRole('button', { name: 'Đóng' }).click();
    await expect(page.getByRole('dialog')).not.toBeVisible();
  });

  test('RequestDetailModal đóng khi phím Escape', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/leader/team-roster**', route =>
      route.fulfill({
        status: 200,
        json: [makeRosterItem({
          hasPendingRequest: true,
          pendingRequestId: 'req-1',
          pendingRequestCategory: 'EXCEPTION',
        })],
      })
    );
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.goto('/leader/dashboard');

    await page.getByRole('button', { name: 'Xem chi tiết' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).not.toBeVisible();
  });
});
