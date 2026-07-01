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

function makeRecord(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    employeeId: 'emp-id',
    shiftId: 'shift-1',
    shiftName: 'Ca Sáng',
    shiftStartTime: '08:00',
    shiftEndTime: '17:00',
    date: '2026-06-30',
    checkInTime: '2026-06-30T01:10:00',
    checkInIp: '203.0.113.10',
    checkInLat: 10.77,
    checkInLng: 106.69,
    checkInPhotoUrl: null,
    checkOutTime: '2026-06-30T10:00:00',
    checkOutIp: null,
    checkOutLat: null,
    checkOutLng: null,
    checkOutPhotoUrl: null,
    attendanceStatus: 'ON_TIME',
    approvalSubStatus: null,
    isClientSite: false,
    gpsUnavailable: false,
    suspiciousLocation: false,
    isAdminOverride: false,
    version: 0,
    createdAt: '2026-06-30T01:10:00',
    ...overrides,
  };
}

const latePage = (id = 'rec-late') => ({
  content: [makeRecord(id, { attendanceStatus: 'LATE_IN' })],
  totalElements: 1, totalPages: 1, size: 20, number: 0,
});

const incompletePage = (id = 'rec-inc') => ({
  content: [makeRecord(id, { attendanceStatus: 'INCOMPLETE', checkOutTime: null })],
  totalElements: 1, totalPages: 1, size: 20, number: 0,
});

// ── ExceptionRequestForm ─────────────────────────────────────────────────

test.describe('Employee — ExceptionRequestForm (Story 4.1)', () => {
  test.beforeEach(async ({ page }) => {
    await seedEmployeeAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
  });

  test('modal mở khi click "Gửi yêu cầu ngoại lệ" trên bản ghi LATE_IN', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();

    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu ngoại lệ' })).toBeVisible();
    await expect(page.locator('#requestType')).toBeVisible();
    await expect(page.locator('#reason')).toBeVisible();
  });

  test('nút Đóng đóng modal', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();
    await page.getByRole('button', { name: 'Đóng' }).click();

    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu ngoại lệ' })).not.toBeVisible();
  });

  test('nút Hủy đóng modal', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();
    await page.getByRole('button', { name: 'Hủy' }).click();

    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu ngoại lệ' })).not.toBeVisible();
  });

  test('phím Escape đóng modal', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();
    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu ngoại lệ' })).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu ngoại lệ' })).not.toBeVisible();
  });

  test('lý do < 10 ký tự → lỗi validation', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();
    await page.locator('#requestType').selectOption('LATE_IN');
    await page.locator('#reason').fill('Quá ngắn');
    await page.getByRole('button', { name: 'Gửi', exact: true }).click();

    await expect(page.getByText('Lý do phải có ít nhất 10 ký tự')).toBeVisible();
  });

  test('lý do > 500 ký tự → lỗi validation', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();
    await page.locator('#requestType').selectOption('LATE_IN');
    await page.locator('#reason').fill('A'.repeat(501));
    await page.getByRole('button', { name: 'Gửi', exact: true }).click();

    await expect(page.getByText('Lý do không được vượt quá 500 ký tự')).toBeVisible();
  });

  test('gửi thành công → toast success + modal đóng', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.route('**/api/requests/exception', route =>
      route.fulfill({
        status: 201,
        json: {
          id: 'req-1', attendanceRecordId: 'rec-late', employeeId: 'emp-id',
          requestType: 'LATE_IN', reason: 'Lý do chính đáng đủ dài để vượt validation',
          status: 'PENDING', reviewedBy: null, reviewReason: null,
          createdAt: '2026-06-30T02:00:00', updatedAt: '2026-06-30T02:00:00',
        },
      })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();
    await page.locator('#requestType').selectOption('LATE_IN');
    await page.locator('#reason').fill('Lý do chính đáng đủ dài để vượt validation');
    await page.getByRole('button', { name: 'Gửi', exact: true }).click();

    await expect(page.getByText('Yêu cầu ngoại lệ được gửi thành công')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu ngoại lệ' })).not.toBeVisible();
  });

  test('lỗi PENDING_REQUEST_EXISTS → toast lỗi cụ thể', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.route('**/api/requests/exception', route =>
      route.fulfill({ status: 409, json: { error: 'PENDING_REQUEST_EXISTS' } })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();
    await page.locator('#requestType').selectOption('LATE_IN');
    await page.locator('#reason').fill('Lý do chính đáng đủ dài để vượt validation');
    await page.getByRole('button', { name: 'Gửi', exact: true }).click();

    await expect(page.getByText('Đã có yêu cầu ngoại lệ chưa xử lý cho bản ghi này')).toBeVisible();
  });

  test('lỗi server khác → toast lỗi chung', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.route('**/api/requests/exception', route =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();
    await page.locator('#requestType').selectOption('LATE_IN');
    await page.locator('#reason').fill('Lý do chính đáng đủ dài để vượt validation');
    await page.getByRole('button', { name: 'Gửi', exact: true }).click();

    await expect(page.getByText('Có lỗi xảy ra, vui lòng thử lại')).toBeVisible();
  });

  test('dialog có role="dialog" và aria-modal="true"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog).toHaveAttribute('aria-modal', 'true');
  });

  test('nút Đóng có aria-label="Đóng"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();

    await expect(page.getByRole('button', { name: 'Đóng' })).toHaveAttribute('aria-label', 'Đóng');
  });

  test('dropdown loại yêu cầu có đủ 4 lựa chọn', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: latePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).click();

    await expect(page.locator('#requestType option[value="LATE_IN"]')).toBeAttached();
    await expect(page.locator('#requestType option[value="EARLY_OUT"]')).toBeAttached();
    await expect(page.locator('#requestType option[value="HALF_DAY"]')).toBeAttached();
    await expect(page.locator('#requestType option[value="LATE_IN_EARLY_OUT"]')).toBeAttached();
  });
});

// ── AdjustmentRequestForm ─────────────────────────────────────────────────

test.describe('Employee — AdjustmentRequestForm (Story 4.1)', () => {
  test.beforeEach(async ({ page }) => {
    await seedEmployeeAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
  });

  test('modal mở khi click "Gửi yêu cầu điều chỉnh" trên bản ghi INCOMPLETE', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: incompletePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' }).click();

    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu điều chỉnh' })).toBeVisible();
    await expect(page.locator('#proposedCheckoutTime')).toBeVisible();
    await expect(page.locator('#reason')).toBeVisible();
  });

  test('nút Đóng đóng modal điều chỉnh', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: incompletePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' }).click();
    await page.getByRole('button', { name: 'Đóng' }).click();

    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu điều chỉnh' })).not.toBeVisible();
  });

  test('phím Escape đóng modal điều chỉnh', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: incompletePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' }).click();
    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu điều chỉnh' })).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu điều chỉnh' })).not.toBeVisible();
  });

  test('hiển thị thông tin giờ check-in trong form', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: incompletePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' }).click();

    await expect(page.getByText(/Check-in:/)).toBeVisible();
  });

  test('lý do < 10 ký tự → lỗi validation', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: incompletePage() })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' }).click();
    await page.locator('#proposedCheckoutTime').fill('2026-06-30T18:00');
    await page.locator('#reason').fill('Quá ngắn');
    await page.getByRole('button', { name: 'Gửi', exact: true }).click();

    await expect(page.getByText('Lý do phải có ít nhất 10 ký tự')).toBeVisible();
  });

  test('gửi thành công → toast success + modal đóng', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: incompletePage() })
    );
    await page.route('**/api/requests/adjustment', route =>
      route.fulfill({
        status: 201,
        json: {
          id: 'adj-1', attendanceRecordId: 'rec-inc', employeeId: 'emp-id',
          proposedCheckoutTime: '2026-06-30T11:00:00Z',
          reason: 'Lý do điều chỉnh hợp lý đủ dài và hợp lệ',
          status: 'PENDING', reviewedBy: null, reviewReason: null,
          createdAt: '2026-06-30T02:00:00', updatedAt: '2026-06-30T02:00:00',
        },
      })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' }).click();
    await page.locator('#proposedCheckoutTime').fill('2026-06-30T18:00');
    await page.locator('#reason').fill('Lý do điều chỉnh hợp lý đủ dài và hợp lệ');
    await page.getByRole('button', { name: 'Gửi', exact: true }).click();

    await expect(page.getByText('Yêu cầu điều chỉnh được gửi thành công')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Gửi yêu cầu điều chỉnh' })).not.toBeVisible();
  });

  test('lỗi PENDING_REQUEST_EXISTS → toast lỗi cụ thể', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: incompletePage() })
    );
    await page.route('**/api/requests/adjustment', route =>
      route.fulfill({ status: 409, json: { error: 'PENDING_REQUEST_EXISTS' } })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' }).click();
    await page.locator('#proposedCheckoutTime').fill('2026-06-30T18:00');
    await page.locator('#reason').fill('Lý do điều chỉnh hợp lý đủ dài và hợp lệ');
    await page.getByRole('button', { name: 'Gửi', exact: true }).click();

    await expect(page.getByText('Đã có yêu cầu điều chỉnh chưa xử lý cho bản ghi này')).toBeVisible();
  });

  test('lỗi INVALID_CHECKOUT_TIME → toast lỗi cụ thể', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: incompletePage() })
    );
    await page.route('**/api/requests/adjustment', route =>
      route.fulfill({ status: 400, json: { error: 'INVALID_CHECKOUT_TIME' } })
    );
    await page.goto('/history');

    await page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' }).click();
    await page.locator('#proposedCheckoutTime').fill('2026-06-30T18:00');
    await page.locator('#reason').fill('Lý do điều chỉnh hợp lý đủ dài và hợp lệ');
    await page.getByRole('button', { name: 'Gửi', exact: true }).click();

    await expect(page.getByText('Thời gian checkout phải sau thời gian check-in')).toBeVisible();
  });
});
