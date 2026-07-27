import { test, expect } from '@playwright/test';
import { seedAdminAuth } from '../support/auth';
import {
  TWO_ATTENDANCE_RECORDS_PAGE as MOCK_PAGE,
  EMPTY_ATTENDANCE_PAGE as EMPTY_PAGE,
  MOCK_EMPLOYEES,
} from '../support/fixtures';

// ── helpers ───────────────────────────────────────────────────────────────

// ── Admin Smart Attendance Filter by Status (Story 9.2) ────────────────────

test.describe('Admin — Lọc bảng công thông minh theo trạng thái (Story 9.2)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.route('**/api/admin/employees**', route =>
      route.fulfill({ status: 200, json: MOCK_EMPLOYEES })
    );
  });

  test('hiển thị đầy đủ các tùy chọn trạng thái trong bộ lọc', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await expect(page.getByText('Đúng giờ').first()).toBeVisible();
    await expect(page.getByText('Đi muộn').first()).toBeVisible();
    await expect(page.getByText('Về sớm').first()).toBeVisible();
    await expect(page.getByText('Muộn & Sớm').first()).toBeVisible();
    await expect(page.getByText('Nửa ngày').first()).toBeVisible();
    await expect(page.getByText('Vắng mặt').first()).toBeVisible();
    await expect(page.getByText('Thiếu checkout').first()).toBeVisible();
  });

  test('chọn nhiều trạng thái gửi đúng nhiều query param status', async ({ page }) => {
    let requestedUrl = '';
    await page.route('**/api/admin/attendance?**', async (route) => {
      requestedUrl = route.request().url();
      await route.fulfill({ status: 200, json: MOCK_PAGE });
    });
    await page.goto('/admin/attendance');

    await page.getByRole('checkbox').nth(1).check(); // Đi muộn (LATE_IN)
    await page.getByRole('checkbox').nth(5).check(); // Vắng mặt (ABSENT)

    await expect.poll(() => requestedUrl).toContain('status=LATE_IN');
    await expect.poll(() => requestedUrl).toContain('status=ABSENT');
  });

  test('hiển thị dòng tóm tắt số bản ghi phù hợp với bộ lọc', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('checkbox').first().check();

    await expect(page.getByText('Đang hiển thị 2 bản ghi phù hợp với bộ lọc')).toBeVisible();
  });

  test('bộ lọc không khớp bản ghi nào hiển thị trạng thái trống kèm nút xóa bộ lọc', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: EMPTY_PAGE })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('checkbox').first().check();

    await expect(page.getByText('Không có bản ghi nào phù hợp với bộ lọc đã chọn')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Xóa bộ lọc' }).first()).toBeVisible();
  });

  test('chip bộ lọc riêng lẻ có thể xóa bằng nút ×', async ({ page }) => {
    await page.route('**/api/admin/attendance?**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/attendance');

    await page.getByRole('checkbox').first().check();
    await expect(page.getByText('Bộ lọc:')).toBeVisible();

    await page.getByRole('button', { name: /Xóa bộ lọc Đúng giờ/ }).click();
    await expect(page.getByText('Bộ lọc:')).not.toBeVisible();
  });

  test('không chọn trạng thái nào thì không gửi query param status', async ({ page }) => {
    let requestedUrl = '';
    await page.route('**/api/admin/attendance?**', async (route) => {
      requestedUrl = route.request().url();
      await route.fulfill({ status: 200, json: MOCK_PAGE });
    });
    await page.goto('/admin/attendance');

    await expect.poll(() => requestedUrl).toContain('/api/admin/attendance');
    expect(requestedUrl).not.toContain('status=');
  });
});
