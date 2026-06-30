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
    checkInPhotoUrl: `emp-id/2026-06-30/checkin_${id}.jpg`,
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

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 };

// ── History Page ──────────────────────────────────────────────────────────

test.describe('Employee — Lịch sử chấm công (Story 3.4)', () => {
  test.beforeEach(async ({ page }) => {
    await seedEmployeeAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
  });

  // ── Hiển thị danh sách ────────────────────────────────────────────────

  test('hiển thị tiêu đề Lịch sử chấm công', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: EMPTY_PAGE })
    );
    await page.goto('/history');

    await expect(page.getByRole('heading', { name: 'Lịch sử chấm công' })).toBeVisible();
  });

  test('hiển thị trạng thái rỗng khi không có bản ghi', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: EMPTY_PAGE })
    );
    await page.goto('/history');

    await expect(page.getByText('Không có bản ghi nào trong khoảng thời gian này')).toBeVisible();
  });

  test('hiển thị danh sách bản ghi với ngày và giờ', async ({ page }) => {
    const records = [
      makeRecord('rec-1', { date: '2026-06-30', checkInTime: '2026-06-30T01:00:00', attendanceStatus: 'ON_TIME' }),
      makeRecord('rec-2', { date: '2026-06-29', checkInTime: '2026-06-29T01:30:00', attendanceStatus: 'LATE_IN' }),
    ];
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: { content: records, totalElements: 2, totalPages: 1, size: 20, number: 0 },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('30/06/2026')).toBeVisible();
    await expect(page.getByText('29/06/2026')).toBeVisible();
  });

  // ── Status badges ──────────────────────────────────────────────────────

  test('badge ON_TIME hiển thị "Đúng giờ"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-1', { attendanceStatus: 'ON_TIME' })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('Đúng giờ')).toBeVisible();
  });

  test('badge LATE_IN hiển thị "Đi muộn"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-1', { attendanceStatus: 'LATE_IN' })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('Đi muộn')).toBeVisible();
  });

  test('badge EARLY_OUT hiển thị "Về sớm"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-1', { attendanceStatus: 'EARLY_OUT' })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('Về sớm')).toBeVisible();
  });

  test('badge LATE_IN_EARLY_OUT hiển thị "Muộn & Sớm"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-1', { attendanceStatus: 'LATE_IN_EARLY_OUT' })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('Muộn & Sớm')).toBeVisible();
  });

  test('badge HALF_DAY hiển thị "Nửa ngày"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-1', { attendanceStatus: 'HALF_DAY' })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('Nửa ngày')).toBeVisible();
  });

  test('badge INCOMPLETE hiển thị "Thiếu"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-1', { attendanceStatus: 'INCOMPLETE', checkOutTime: null })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('Thiếu', { exact: true })).toBeVisible();
  });

  test('badge ABSENT hiển thị "Vắng"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-1', { attendanceStatus: 'ABSENT', checkInTime: null, checkOutTime: null })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('Vắng', { exact: true })).toBeVisible();
  });

  // ── Request buttons ────────────────────────────────────────────────────

  test('bản ghi LATE_IN hiển thị nút "Gửi yêu cầu ngoại lệ"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-late', { attendanceStatus: 'LATE_IN' })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' })).toBeVisible();
  });

  test('bản ghi EARLY_OUT hiển thị nút "Gửi yêu cầu ngoại lệ"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-early', { attendanceStatus: 'EARLY_OUT' })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' })).toBeVisible();
  });

  test('bản ghi HALF_DAY hiển thị nút "Gửi yêu cầu ngoại lệ"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-half', { attendanceStatus: 'HALF_DAY' })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' })).toBeVisible();
  });

  test('bản ghi LATE_IN_EARLY_OUT hiển thị nút "Gửi yêu cầu ngoại lệ"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-both', { attendanceStatus: 'LATE_IN_EARLY_OUT' })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' })).toBeVisible();
  });

  test('bản ghi INCOMPLETE hiển thị nút "Gửi yêu cầu điều chỉnh"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-incomplete', { attendanceStatus: 'INCOMPLETE', checkOutTime: null })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' })).toBeVisible();
  });

  test('bản ghi ON_TIME không hiển thị nút request', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-ok', { attendanceStatus: 'ON_TIME' })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' })).not.toBeVisible();
  });

  test('bản ghi ABSENT không hiển thị nút request', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-absent', { attendanceStatus: 'ABSENT', checkInTime: null, checkOutTime: null })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' })).not.toBeVisible();
  });

  // ── Client Site badge ──────────────────────────────────────────────────

  test('bản ghi is_client_site=true hiển thị badge "Ngoài VP"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-cs', { isClientSite: true })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('Ngoài VP')).toBeVisible();
  });

  test('bản ghi is_client_site=false không hiển thị badge "Ngoài VP"', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-office', { isClientSite: false })],
          totalElements: 1, totalPages: 1, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('Ngoài VP')).not.toBeVisible();
  });

  // ── Phân trang ────────────────────────────────────────────────────────

  test('hiển thị phân trang khi có nhiều hơn 1 trang', async ({ page }) => {
    const records = Array.from({ length: 3 }, (_, i) =>
      makeRecord(`rec-${i}`, { date: `2026-06-${28 - i}` })
    );
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: { content: records, totalElements: 25, totalPages: 2, size: 20, number: 0 },
      })
    );
    await page.goto('/history');

    await expect(page.getByRole('button', { name: '← Trước' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sau →' })).toBeVisible();
    await expect(page.getByText('Trang 1 / 2')).toBeVisible();
  });

  test('nút Trước disabled ở trang đầu', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: {
          content: [makeRecord('rec-1')],
          totalElements: 25, totalPages: 2, size: 20, number: 0,
        },
      })
    );
    await page.goto('/history');

    await expect(page.getByRole('button', { name: '← Trước' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Sau →' })).not.toBeDisabled();
  });

  test('không hiển thị phân trang khi chỉ có 1 trang', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: { content: [makeRecord('rec-1')], totalElements: 1, totalPages: 1, size: 20, number: 0 },
      })
    );
    await page.goto('/history');

    await expect(page.getByRole('button', { name: '← Trước' })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'Sau →' })).not.toBeVisible();
  });

  // ── Date range filter ──────────────────────────────────────────────────

  test('hiển thị date range inputs (Từ ngày / Đến ngày)', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 200, json: EMPTY_PAGE })
    );
    await page.goto('/history');

    await expect(page.locator('#history-from')).toBeVisible();
    await expect(page.locator('#history-to')).toBeVisible();
  });

  test('thay đổi "Từ ngày" cập nhật bộ lọc và gọi lại API', async ({ page }) => {
    const requestUrls: string[] = [];
    await page.route('**/api/attendance/history**', route => {
      requestUrls.push(route.request().url());
      route.fulfill({ status: 200, json: EMPTY_PAGE });
    });
    await page.goto('/history');

    const fromInput = page.locator('#history-from');
    await expect(fromInput).toBeVisible();

    // Playwright's fill() on date inputs sets the value and fires the 'input' event,
    // which React 18 picks up via its synthetic event system.
    await fromInput.fill('2026-06-01');

    // The input value must reflect the chosen date (UI binding verified)
    await expect(fromInput).toHaveValue('2026-06-01');

    // TanStack Query detects the query-key change and refetches — wait for that request
    await page.waitForFunction(
      (urls: string[]) => urls.some(u => u.includes('from=2026-06-01')),
      requestUrls,
      { timeout: 5000 }
    );
    expect(requestUrls.some(u => u.includes('from=2026-06-01'))).toBe(true);
  });

  // ── Loading & Error states ────────────────────────────────────────────

  test('hiển thị skeleton loader khi đang tải lịch sử', async ({ page }) => {
    await page.route('**/api/attendance/history**', async route => {
      await new Promise(resolve => setTimeout(resolve, 500));
      await route.fulfill({ status: 200, json: EMPTY_PAGE });
    });
    await page.goto('/history');

    await expect(page.locator('.animate-pulse').first()).toBeVisible();
  });

  test('API lỗi → hiển thị thông báo lỗi', async ({ page }) => {
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } })
    );
    await page.goto('/history');

    await expect(page.getByText('Không thể tải lịch sử')).toBeVisible();
  });

  // ── Multiple records on same page ─────────────────────────────────────

  test('hiển thị nhiều bản ghi với trạng thái khác nhau', async ({ page }) => {
    const records = [
      makeRecord('r1', { date: '2026-06-30', attendanceStatus: 'ON_TIME' }),
      makeRecord('r2', { date: '2026-06-29', attendanceStatus: 'LATE_IN' }),
      makeRecord('r3', { date: '2026-06-28', attendanceStatus: 'EARLY_OUT' }),
      makeRecord('r4', { date: '2026-06-27', attendanceStatus: 'INCOMPLETE', checkOutTime: null }),
    ];
    await page.route('**/api/attendance/history**', route =>
      route.fulfill({
        status: 200,
        json: { content: records, totalElements: 4, totalPages: 1, size: 20, number: 0 },
      })
    );
    await page.goto('/history');

    await expect(page.getByText('Đúng giờ')).toBeVisible();
    await expect(page.getByText('Đi muộn')).toBeVisible();
    await expect(page.getByText('Về sớm')).toBeVisible();
    // INCOMPLETE record should show adjustment request button
    await expect(page.getByRole('button', { name: 'Gửi yêu cầu điều chỉnh' })).toBeVisible();
    // LATE_IN record should show exception request button
    await expect(page.getByRole('button', { name: 'Gửi yêu cầu ngoại lệ' }).first()).toBeVisible();
  });
});
