import { test, expect, type Page } from '@playwright/test';

// ── helpers ───────────────────────────────────────────────────────────────

async function seedEmployeeAuth(page: Page) {
  await page.addInitScript(
    (storage: { key: string; value: unknown }) => {
      localStorage.setItem(storage.key, JSON.stringify(storage.value));
    },
    {
      key: 'itx-auth',
      value: {
        state: {
          user: { id: 'emp-id', username: 'emp1', fullName: 'Nguyen Van A', role: 'EMPLOYEE', mustChangePassword: false },
          isAuthenticated: true,
        },
        version: 0,
      },
    }
  );
}

function makeOtRequest(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    requestCategory: 'OT',
    employeeId: 'emp-id',
    attendanceRecordId: null,
    attendanceDate: null,
    requestType: null,
    proposedCheckoutTime: null,
    checkInTime: null,
    checkOutTime: null,
    reason: 'Hoàn thành báo cáo cuối tháng gấp',
    status: 'PENDING',
    reviewedBy: null,
    reviewReason: null,
    createdAt: '2026-07-01T02:00:00',
    updatedAt: '2026-07-01T02:00:00',
    leaveType: null,
    startDate: null,
    endDate: null,
    totalDays: null,
    plannedDate: '2026-07-05',
    plannedOtHours: 2.5,
    ...overrides,
  };
}

const tomorrow = () => {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return d.toISOString().split('T')[0];
};

async function setupRequestsPage(page: Page) {
  await seedEmployeeAuth(page);
  await page.route('**/api/notifications/pending', route =>
    route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
  );
  await page.route('**/api/requests/me', route =>
    route.fulfill({ status: 200, json: [] })
  );
  await page.route('**/api/requests/leave-balance', route =>
    route.fulfill({ status: 200, json: [] })
  );
}

async function openOtModal(page: Page) {
  await page.goto('/requests');
  await page.getByRole('button', { name: 'Xin OT' }).click();
  await expect(page.getByRole('heading', { name: 'Xin làm thêm giờ (OT)' })).toBeVisible();
}

// ── EmployeeRequestsPage — "Xin OT" entry point (Story 8.1) ─────────────────

test.describe('Employee — Requests page "Xin OT" (Story 8.1)', () => {
  test.beforeEach(async ({ page }) => {
    await setupRequestsPage(page);
  });

  test('nút "Xin OT" hiển thị trên trang Yêu cầu', async ({ page }) => {
    await page.goto('/requests');

    await expect(page.getByRole('button', { name: 'Xin OT' })).toBeVisible();
  });

  test('click "Xin OT" mở OtRequestModal', async ({ page }) => {
    await openOtModal(page);

    await expect(page.getByRole('dialog')).toBeVisible();
  });

  test('yêu cầu OT xuất hiện trong lịch sử với badge "OT" và số giờ', async ({ page }) => {
    await page.unroute('**/api/requests/me');
    await page.route('**/api/requests/me', route =>
      route.fulfill({ status: 200, json: [makeOtRequest('ot-1')] })
    );
    await page.goto('/requests');

    await expect(page.getByText('OT', { exact: true })).toBeVisible();
    await expect(page.getByText('2.5 giờ')).toBeVisible();
  });
});

// ── OtRequestModal (Story 8.1) ───────────────────────────────────────────

test.describe('Employee — OtRequestModal (Story 8.1)', () => {
  test.beforeEach(async ({ page }) => {
    await setupRequestsPage(page);
  });

  test('hiển thị đủ 3 trường: Ngày làm OT, Số giờ dự kiến, Lý do', async ({ page }) => {
    await openOtModal(page);

    await expect(page.getByText('Ngày làm OT')).toBeVisible();
    await expect(page.getByText('Số giờ dự kiến (0.5 – 8 giờ)')).toBeVisible();
    await expect(page.getByText('Lý do', { exact: true })).toBeVisible();
    await expect(page.locator('input[type="date"]')).toBeVisible();
    await expect(page.locator('input[type="number"]')).toBeVisible();
    await expect(page.locator('textarea')).toBeVisible();
  });

  test('dialog có role="dialog" và aria-modal="true"', async ({ page }) => {
    await openOtModal(page);

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog).toHaveAttribute('aria-modal', 'true');
  });

  test('nút Đóng có aria-label="Đóng" và đóng modal', async ({ page }) => {
    await openOtModal(page);

    const closeBtn = page.getByRole('button', { name: 'Đóng' });
    await expect(closeBtn).toHaveAttribute('aria-label', 'Đóng');
    await closeBtn.click();

    await expect(page.getByRole('heading', { name: 'Xin làm thêm giờ (OT)' })).not.toBeVisible();
  });

  test('phím Escape đóng modal', async ({ page }) => {
    await openOtModal(page);

    await page.keyboard.press('Escape');

    await expect(page.getByRole('heading', { name: 'Xin làm thêm giờ (OT)' })).not.toBeVisible();
  });

  test('nút "Gửi yêu cầu OT" bị disabled khi form chưa hợp lệ', async ({ page }) => {
    await openOtModal(page);

    await expect(page.getByRole('button', { name: 'Gửi yêu cầu OT' })).toBeDisabled();
  });

  test('lý do < 10 ký tự → nút Gửi vẫn bị disabled', async ({ page }) => {
    await openOtModal(page);

    await page.locator('input[type="date"]').fill(tomorrow());
    await page.locator('input[type="number"]').fill('2');
    await page.locator('textarea').fill('Quá ngắn');

    await expect(page.getByRole('button', { name: 'Gửi yêu cầu OT' })).toBeDisabled();
  });

  test('số giờ ngoài khoảng 0.5–8 → nút Gửi vẫn bị disabled', async ({ page }) => {
    await openOtModal(page);

    await page.locator('input[type="date"]').fill(tomorrow());
    await page.locator('input[type="number"]').fill('9');
    await page.locator('textarea').fill('Lý do làm OT hợp lệ đủ mười ký tự');

    await expect(page.getByRole('button', { name: 'Gửi yêu cầu OT' })).toBeDisabled();
  });

  test('điền hợp lệ → gửi thành công → toast success + modal đóng', async ({ page }) => {
    await page.route('**/api/requests/ot', route =>
      route.fulfill({
        status: 201,
        json: makeOtRequest('ot-1', { status: 'PENDING' }),
      })
    );
    await openOtModal(page);

    await page.locator('input[type="date"]').fill(tomorrow());
    await page.locator('input[type="number"]').fill('2.5');
    await page.locator('textarea').fill('Hoàn thành báo cáo cuối tháng gấp');
    await page.getByRole('button', { name: 'Gửi yêu cầu OT' }).click();

    await expect(page.getByText('Đã gửi yêu cầu OT')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Xin làm thêm giờ (OT)' })).not.toBeVisible();
  });

  test('lỗi DUPLICATE_OT_REQUEST (409) → toast hiển thị message từ server', async ({ page }) => {
    await page.route('**/api/requests/ot', route =>
      route.fulfill({
        status: 409,
        json: { error: 'DUPLICATE_OT_REQUEST', message: 'Đã có yêu cầu OT PENDING cho ngày này' },
      })
    );
    await openOtModal(page);

    await page.locator('input[type="date"]').fill(tomorrow());
    await page.locator('input[type="number"]').fill('2');
    await page.locator('textarea').fill('Lý do làm OT hợp lệ đủ mười ký tự');
    await page.getByRole('button', { name: 'Gửi yêu cầu OT' }).click();

    await expect(page.getByText('Đã có yêu cầu OT PENDING cho ngày này')).toBeVisible();
  });

  test('lỗi server không có message → toast lỗi chung', async ({ page }) => {
    await page.route('**/api/requests/ot', route =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } })
    );
    await openOtModal(page);

    await page.locator('input[type="date"]').fill(tomorrow());
    await page.locator('input[type="number"]').fill('2');
    await page.locator('textarea').fill('Lý do làm OT hợp lệ đủ mười ký tự');
    await page.getByRole('button', { name: 'Gửi yêu cầu OT' }).click();

    await expect(page.getByText('Có lỗi xảy ra, vui lòng thử lại')).toBeVisible();
  });
});
