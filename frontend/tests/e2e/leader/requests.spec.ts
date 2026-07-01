import { test, expect, type Page } from '@playwright/test';

// ── helpers ───────────────────────────────────────────────────────────────

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

function makeAdjustmentRequest(id: string, overrides: Record<string, unknown> = {}) {
  return makeRequest(id, {
    requestCategory: 'ADJUSTMENT',
    requestType: null,
    proposedCheckoutTime: '2026-06-30T11:00:00',
    reason: 'Quên check-out do họp đột xuất kéo dài',
    ...overrides,
  });
}

function makeLeaveRequest(id: string, overrides: Record<string, unknown> = {}) {
  return makeRequest(id, {
    requestCategory: 'LEAVE',
    requestType: null,
    attendanceRecordId: null,
    attendanceDate: null,
    reason: 'Về quê thăm gia đình dịp lễ',
    leaveType: 'ANNUAL',
    startDate: '2026-07-10',
    endDate: '2026-07-12',
    totalDays: 3,
    ...overrides,
  });
}

// ── Leader Requests Page ──────────────────────────────────────────────────

test.describe('Leader — Duyệt Yêu cầu (Story 4.3)', () => {
  test.beforeEach(async ({ page }) => {
    await seedLeaderAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
  });

  // ── Layout & tabs ──────────────────────────────────────────────────────

  test('hiển thị tiêu đề "Duyệt Yêu cầu"', async ({ page }) => {
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.goto('/leader/requests');

    await expect(page.getByRole('heading', { name: 'Duyệt Yêu cầu' })).toBeVisible();
  });

  test('hiển thị 3 tabs: Chờ duyệt, Đã duyệt, Từ chối', async ({ page }) => {
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.goto('/leader/requests');

    await expect(page.getByRole('button', { name: /Chờ duyệt/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Đã duyệt/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Từ chối/ })).toBeVisible();
  });

  test('tab Chờ duyệt hiển thị badge số lượng khi có yêu cầu', async ({ page }) => {
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [makeRequest('req-1'), makeRequest('req-2')] })
    );
    await page.goto('/leader/requests');

    const pendingTab = page.getByRole('button', { name: /Chờ duyệt/ });
    await expect(pendingTab.locator('span')).toBeVisible();
    await expect(pendingTab.locator('span')).toContainText('2');
  });

  // ── Empty states ───────────────────────────────────────────────────────

  test('trạng thái rỗng tab Chờ duyệt', async ({ page }) => {
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.goto('/leader/requests');

    await expect(page.getByText('Không có yêu cầu chờ duyệt')).toBeVisible();
  });

  test('skeleton loader hiển thị khi đang tải', async ({ page }) => {
    await page.route('**/api/requests/pending', async route => {
      await new Promise(resolve => setTimeout(resolve, 500));
      await route.fulfill({ status: 200, json: [] });
    });
    await page.goto('/leader/requests');

    await expect(page.locator('.animate-pulse').first()).toBeVisible();
  });

  // ── Request cards ──────────────────────────────────────────────────────

  test('hiển thị thẻ yêu cầu với tên nhân viên, ngày và lý do', async ({ page }) => {
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [makeRequest('req-1')] })
    );
    await page.goto('/leader/requests');

    await expect(page.getByText('Nguyen Van A')).toBeVisible();
    await expect(page.getByText('Lý do xin ngoại lệ hợp lý và chi tiết')).toBeVisible();
    await expect(page.getByText('Ngoại lệ · 2026-06-30')).toBeVisible();
  });

  test('hiển thị yêu cầu điều chỉnh với loại "Điều chỉnh"', async ({ page }) => {
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [makeAdjustmentRequest('req-adj')] })
    );
    await page.goto('/leader/requests');

    await expect(page.getByText('Điều chỉnh · 2026-06-30')).toBeVisible();
  });

  // ── Modal interactions ─────────────────────────────────────────────────

  test('click thẻ yêu cầu mở RequestDetailModal', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.goto('/leader/requests');

    await page.locator('button').filter({ hasText: 'Nguyen Van A' }).click();

    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText('Chi tiết yêu cầu')).toBeVisible();
  });

  test('RequestDetailModal hiển thị đầy đủ thông tin yêu cầu ngoại lệ', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.goto('/leader/requests');

    await page.locator('button').filter({ hasText: 'Nguyen Van A' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('Ngoại lệ — Đi muộn')).toBeVisible();
    await expect(dialog.getByText('Lý do xin ngoại lệ hợp lý và chi tiết')).toBeVisible();
  });

  test('RequestDetailModal hiển thị giờ ra đề xuất cho yêu cầu điều chỉnh', async ({ page }) => {
    const request = makeAdjustmentRequest('req-adj');
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.goto('/leader/requests');

    await page.locator('button').filter({ hasText: 'Nguyen Van A' }).click();

    await expect(page.getByText('Giờ ra đề xuất')).toBeVisible();
    await expect(page.getByText('Điều chỉnh giờ ra')).toBeVisible();
  });

  // ── Approve & Reject ───────────────────────────────────────────────────

  test('duyệt yêu cầu từ modal → toast success + modal đóng', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.route('**/api/requests/*/approve', route =>
      route.fulfill({ status: 200, json: { ...request, status: 'APPROVED' } })
    );
    await page.goto('/leader/requests');

    await page.locator('button').filter({ hasText: 'Nguyen Van A' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Duyệt', exact: true }).click();

    await expect(page.getByText('Đã duyệt yêu cầu thành công')).toBeVisible();
    await expect(page.getByRole('dialog')).not.toBeVisible();
  });

  test('từ chối yêu cầu với lý do → toast success + modal đóng', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.route('**/api/requests/*/reject', route =>
      route.fulfill({ status: 200, json: { ...request, status: 'REJECTED' } })
    );
    await page.goto('/leader/requests');

    await page.locator('button').filter({ hasText: 'Nguyen Van A' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Từ chối' }).click();
    await page.getByRole('textbox', { name: 'Lý do từ chối' }).fill('Lý do không chính đáng');
    await page.getByRole('button', { name: 'Xác nhận từ chối' }).click();

    await expect(page.getByText('Đã từ chối yêu cầu')).toBeVisible();
    await expect(page.getByRole('dialog')).not.toBeVisible();
  });

  test('huỷ từ chối → ẩn RejectionReasonModal, giữ modal chi tiết', async ({ page }) => {
    const request = makeRequest('req-1');
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.goto('/leader/requests');

    await page.locator('button').filter({ hasText: 'Nguyen Van A' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Từ chối' }).click();
    await expect(page.getByText('Lý do từ chối')).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByText('Lý do từ chối')).not.toBeVisible();
    await expect(page.getByRole('dialog')).toBeVisible();
  });

  // ── Tab switching ──────────────────────────────────────────────────────

  test('chuyển sang tab Đã duyệt → tải danh sách approved', async ({ page }) => {
    const approvedRequest = makeRequest('req-approved', {
      status: 'APPROVED',
      employeeName: 'Le Van C',
    });
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.route(/\/api\/requests\?.*status=APPROVED/, route =>
      route.fulfill({ status: 200, json: [approvedRequest] })
    );
    await page.goto('/leader/requests');

    await page.getByRole('button', { name: /Đã duyệt/ }).click();

    await expect(page.getByText('Le Van C')).toBeVisible();
  });

  test('chuyển sang tab Từ chối → tải danh sách rejected', async ({ page }) => {
    const rejectedRequest = makeRequest('req-rejected', {
      status: 'REJECTED',
      employeeName: 'Pham Thi D',
      reviewReason: 'Không đủ lý do',
    });
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.route(/\/api\/requests\?.*status=REJECTED/, route =>
      route.fulfill({ status: 200, json: [rejectedRequest] })
    );
    await page.goto('/leader/requests');

    await page.getByRole('button', { name: /Từ chối/ }).click();

    await expect(page.getByText('Pham Thi D')).toBeVisible();
  });

  test('tab Từ chối rỗng hiển thị "Danh sách trống"', async ({ page }) => {
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.route(/\/api\/requests\?.*status=REJECTED/, route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.goto('/leader/requests');

    await page.getByRole('button', { name: /Từ chối/ }).click();

    await expect(page.getByText('Danh sách trống')).toBeVisible();
  });

  // ── Leave requests (Story 6.1) ─────────────────────────────────────────

  test('hiển thị yêu cầu nghỉ phép với loại "Nghỉ phép", loại phép và khoảng ngày', async ({ page }) => {
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [makeLeaveRequest('leave-1')] })
    );
    await page.goto('/leader/requests');

    await expect(page.getByText('Nghỉ phép · Phép năm · 2026-07-10 – 2026-07-12 · 3 ngày')).toBeVisible();
  });

  test('RequestDetailModal hiển thị ngày bắt đầu/kết thúc/tổng ngày nghỉ cho yêu cầu nghỉ phép', async ({ page }) => {
    const request = makeLeaveRequest('leave-1');
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.goto('/leader/requests');

    await page.locator('button').filter({ hasText: 'Nguyen Van A' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('Nghỉ phép — Phép năm')).toBeVisible();
    await expect(dialog.getByText('2026-07-10')).toBeVisible();
    await expect(dialog.getByText('2026-07-12')).toBeVisible();
    await expect(dialog.getByText('3 ngày')).toBeVisible();
  });

  test('duyệt yêu cầu nghỉ phép từ modal → toast success + modal đóng', async ({ page }) => {
    const request = makeLeaveRequest('leave-1');
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.route('**/api/requests/*/approve', route =>
      route.fulfill({ status: 200, json: { ...request, status: 'APPROVED' } })
    );
    await page.goto('/leader/requests');

    await page.locator('button').filter({ hasText: 'Nguyen Van A' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Duyệt', exact: true }).click();

    await expect(page.getByText('Đã duyệt yêu cầu thành công')).toBeVisible();
    await expect(page.getByRole('dialog')).not.toBeVisible();
  });

  test('từ chối yêu cầu nghỉ phép với lý do → toast success', async ({ page }) => {
    const request = makeLeaveRequest('leave-1');
    await page.route('**/api/requests/pending', route =>
      route.fulfill({ status: 200, json: [request] })
    );
    await page.route('**/api/requests/*/reject', route =>
      route.fulfill({ status: 200, json: { ...request, status: 'REJECTED' } })
    );
    await page.goto('/leader/requests');

    await page.locator('button').filter({ hasText: 'Nguyen Van A' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Từ chối' }).click();
    await page.getByRole('textbox', { name: 'Lý do từ chối' }).fill('Không đủ nhân sự trong giai đoạn này');
    await page.getByRole('button', { name: 'Xác nhận từ chối' }).click();

    await expect(page.getByText('Đã từ chối yêu cầu')).toBeVisible();
  });
});
