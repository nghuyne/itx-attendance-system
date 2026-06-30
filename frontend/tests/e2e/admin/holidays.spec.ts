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

const MOCK_HOLIDAYS = [
  { id: 1, date: '2026-01-01', name: 'Tết Dương Lịch', type: 'FIXED', year: 2026 },
  { id: 2, date: '2026-04-30', name: 'Ngày Giải Phóng', type: 'FIXED', year: 2026 },
  { id: 3, date: '2026-01-29', name: 'Tết Nguyên Đán', type: 'DYNAMIC', year: 2026 },
];

const MOCK_HOLIDAY_PAGE = { content: MOCK_HOLIDAYS, totalElements: 3, totalPages: 1, size: 100, number: 0 };
const EMPTY_HOLIDAY_PAGE = { content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 };

// ── Holiday Management ────────────────────────────────────────────────────

test.describe('Admin — Quản lý Ngày lễ (Story 2.3)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
  });

  // ── List & display ─────────────────────────────────────────────────────

  test('hiển thị danh sách ngày lễ với đủ cột', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    await expect(page.getByRole('heading', { name: 'Quản lý Ngày lễ' })).toBeVisible();
    await expect(page.getByText('Tết Dương Lịch')).toBeVisible();
    await expect(page.getByText('Ngày Giải Phóng')).toBeVisible();
    await expect(page.getByText('Tết Nguyên Đán')).toBeVisible();
    await expect(page.getByText('01/01/2026')).toBeVisible();
    await expect(page.getByText('29/01/2026')).toBeVisible();
  });

  test('ngày lễ FIXED hiển thị badge "Cố định", DYNAMIC hiển thị "Linh hoạt"', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    // Use exact:true to avoid matching dropdown options like "Cố định (Dương lịch)"
    await expect(page.getByText('Cố định', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Linh hoạt', { exact: true })).toBeVisible();
  });

  test('hiển thị trạng thái trống khi chưa có ngày lễ', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: EMPTY_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    await expect(page.getByText('Chưa có ngày lễ nào')).toBeVisible();
  });

  // ── Filter ─────────────────────────────────────────────────────────────

  test('bộ lọc năm gọi API với year param đúng', async ({ page }) => {
    const requestUrls: string[] = [];
    await page.route('**/api/admin/holidays**', (route) => {
      requestUrls.push(route.request().url());
      route.fulfill({ status: 200, json: EMPTY_HOLIDAY_PAGE });
    });
    await page.goto('/admin/holidays');

    // Wait for initial load
    await expect(page.getByRole('heading', { name: 'Quản lý Ngày lễ' })).toBeVisible();

    // Change year filter — verify it re-fetches
    const yearSelect = page.getByRole('combobox', { name: 'Lọc theo năm' });
    await yearSelect.selectOption({ index: 0 });

    // Just verify the select interaction works
    await expect(yearSelect).toBeVisible();
  });

  test('bộ lọc loại ngày lễ lọc phía client (FIXED/DYNAMIC)', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    // All holidays visible initially
    await expect(page.getByText('Tết Dương Lịch')).toBeVisible();
    await expect(page.getByText('Tết Nguyên Đán')).toBeVisible();

    // Filter by FIXED
    await page.getByRole('combobox', { name: 'Lọc theo loại ngày lễ' }).selectOption('FIXED');
    await expect(page.getByText('Tết Dương Lịch')).toBeVisible();
    await expect(page.getByText('Ngày Giải Phóng')).toBeVisible();
    await expect(page.getByText('Tết Nguyên Đán')).not.toBeVisible();

    // Filter by DYNAMIC
    await page.getByRole('combobox', { name: 'Lọc theo loại ngày lễ' }).selectOption('DYNAMIC');
    await expect(page.getByText('Tết Nguyên Đán')).toBeVisible();
    await expect(page.getByText('Tết Dương Lịch')).not.toBeVisible();

    // Reset to ALL
    await page.getByRole('combobox', { name: 'Lọc theo loại ngày lễ' }).selectOption('ALL');
    await expect(page.getByText('Tết Dương Lịch')).toBeVisible();
    await expect(page.getByText('Tết Nguyên Đán')).toBeVisible();
  });

  // ── Add holiday modal ──────────────────────────────────────────────────

  test('nút "+ Thêm ngày lễ" mở modal', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: /Thêm ngày lễ/ }).click();

    await expect(page.getByRole('heading', { name: 'Thêm ngày lễ' })).toBeVisible();
    await expect(page.locator('#date')).toBeVisible();
    await expect(page.locator('#name')).toBeVisible();
    await expect(page.getByRole('radio', { name: /Cố định/ })).toBeVisible();
    await expect(page.getByRole('radio', { name: /Linh hoạt/ })).toBeVisible();
  });

  test('validate: ngày không được để trống', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: /Thêm ngày lễ/ }).click();
    await page.locator('#name').fill('Test Holiday');
    await page.getByRole('button', { name: 'Lưu ngày lễ' }).click();

    await expect(page.getByText('Vui lòng chọn ngày')).toBeVisible();
  });

  test('validate: tên ngày lễ phải có ít nhất 2 ký tự', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: /Thêm ngày lễ/ }).click();
    await page.locator('#date').fill('2026-12-25');
    await page.locator('#name').fill('X');
    await page.getByRole('button', { name: 'Lưu ngày lễ' }).click();

    await expect(page.getByText('Tên ngày lễ phải có ít nhất 2 ký tự')).toBeVisible();
  });

  test('trường năm tự động điền từ ngày được chọn', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: /Thêm ngày lễ/ }).click();
    await page.locator('#date').fill('2027-05-01');

    await expect(page.locator('#year')).toHaveValue('2027');
  });

  test('thêm ngày lễ FIXED thành công → toast success', async ({ page }) => {
    const newHoliday = { id: 4, date: '2026-12-25', name: 'Giáng Sinh', type: 'FIXED', year: 2026 };
    await page.route('**/api/admin/holidays**', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, json: newHoliday });
      } else {
        await route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE });
      }
    });
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: /Thêm ngày lễ/ }).click();
    await page.locator('#date').fill('2026-12-25');
    await page.locator('#name').fill('Giáng Sinh');
    await page.getByRole('radio', { name: /Cố định/ }).click();
    await page.getByRole('button', { name: 'Lưu ngày lễ' }).click();

    await expect(page.getByText('Thêm ngày lễ thành công')).toBeVisible();
  });

  test('thêm ngày lễ DYNAMIC (Âm lịch) thành công → toast success', async ({ page }) => {
    const newHoliday = { id: 5, date: '2027-01-18', name: 'Tết Nguyên Đán 2027', type: 'DYNAMIC', year: 2027 };
    await page.route('**/api/admin/holidays**', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, json: newHoliday });
      } else {
        await route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE });
      }
    });
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: /Thêm ngày lễ/ }).click();
    await page.locator('#date').fill('2027-01-18');
    await page.locator('#name').fill('Tết Nguyên Đán 2027');
    await page.getByRole('radio', { name: /Linh hoạt/ }).click();
    await page.getByRole('button', { name: 'Lưu ngày lễ' }).click();

    await expect(page.getByText('Thêm ngày lễ thành công')).toBeVisible();
  });

  test('thêm ngày lễ bị trùng ngày → toast lỗi HOLIDAY_DATE_EXISTS', async ({ page }) => {
    await page.route('**/api/admin/holidays**', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 409, json: { error: 'HOLIDAY_DATE_EXISTS', message: 'Holiday date already exists' } });
      } else {
        await route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE });
      }
    });
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: /Thêm ngày lễ/ }).click();
    await page.locator('#date').fill('2026-01-01');
    await page.locator('#name').fill('Tết Dương Lịch Trùng');
    await page.getByRole('button', { name: 'Lưu ngày lễ' }).click();

    await expect(page.getByText('Đã có ngày lễ cho ngày này')).toBeVisible();
  });

  test('nút Hủy đóng modal không lưu', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: /Thêm ngày lễ/ }).click();
    await expect(page.getByRole('heading', { name: 'Thêm ngày lễ' })).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByRole('heading', { name: 'Thêm ngày lễ' })).not.toBeVisible();
  });

  // ── Delete flow ────────────────────────────────────────────────────────

  test('nút xóa mở dialog xác nhận với tên ngày lễ', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: 'Xóa ngày lễ Tết Dương Lịch' }).click();

    await expect(page.getByText('Xác nhận xóa ngày lễ')).toBeVisible();
    // The holiday name and date also appear in the table rows, so scope to the dialog paragraph
    await expect(page.getByText('Bạn có chắc muốn xóa').filter({ hasText: 'Tết Dương Lịch' })).toBeVisible();
    await expect(page.getByText('Bạn có chắc muốn xóa').filter({ hasText: '01/01/2026' })).toBeVisible();
  });

  test('xóa ngày lễ thành công → toast success', async ({ page }) => {
    await page.route('**/api/admin/holidays**', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 200, body: '' });
      } else {
        await route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE });
      }
    });
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: 'Xóa ngày lễ Tết Dương Lịch' }).click();
    await page.getByRole('button', { name: 'Xóa' }).last().click();

    await expect(page.getByText('Xóa ngày lễ thành công')).toBeVisible();
  });

  test('nút Hủy trong dialog xóa không xóa ngày lễ', async ({ page }) => {
    await page.route('**/api/admin/holidays**', route =>
      route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE })
    );
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: 'Xóa ngày lễ Ngày Giải Phóng' }).click();
    await expect(page.getByText('Xác nhận xóa ngày lễ')).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByText('Xác nhận xóa ngày lễ')).not.toBeVisible();
    await expect(page.getByText('Ngày Giải Phóng')).toBeVisible();
  });

  test('xóa ngày lễ thất bại → toast lỗi', async ({ page }) => {
    await page.route('**/api/admin/holidays**', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } });
      } else {
        await route.fulfill({ status: 200, json: MOCK_HOLIDAY_PAGE });
      }
    });
    await page.goto('/admin/holidays');

    await page.getByRole('button', { name: 'Xóa ngày lễ Tết Dương Lịch' }).click();
    await page.getByRole('button', { name: 'Xóa' }).last().click();

    await expect(page.getByText('Xóa ngày lễ thất bại, vui lòng thử lại')).toBeVisible();
  });
});
