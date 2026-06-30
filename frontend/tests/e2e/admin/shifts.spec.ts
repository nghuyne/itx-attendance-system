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

const MOCK_SHIFTS = [
  {
    id: 'shift-1', name: 'Ca Sáng', startTime: '08:00', endTime: '17:00',
    checkInOpenMinutes: 30, lateInThreshold: 15, earlyOutThreshold: 15,
    halfDayThreshold: 240, otBuffer: 30, assignedCount: 2,
  },
  {
    id: 'shift-2', name: 'Ca Chiều', startTime: '13:00', endTime: '22:00',
    checkInOpenMinutes: 30, lateInThreshold: 15, earlyOutThreshold: 15,
    halfDayThreshold: 240, otBuffer: 30, assignedCount: 0,
  },
];

const MOCK_PAGE = { content: MOCK_SHIFTS, totalElements: 2, totalPages: 1, size: 100, number: 0 };
const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 };

const MOCK_EMPLOYEES = [
  { id: 'emp-1', username: 'emp1', fullName: 'Nguyen Van A' },
  { id: 'emp-2', username: 'emp2', fullName: 'Tran Thi B' },
];

// ── Shift Management ──────────────────────────────────────────────────────

test.describe('Admin — Quản lý Ca làm việc (Story 2.1)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
  });

  // ── List & empty state ─────────────────────────────────────────────────

  test('hiển thị danh sách ca với đủ cột', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/shifts');

    await expect(page.getByRole('heading', { name: 'Ca làm việc' })).toBeVisible();
    await expect(page.getByText('Ca Sáng')).toBeVisible();
    await expect(page.getByText('Ca Chiều')).toBeVisible();
    await expect(page.getByText('08:00')).toBeVisible();
    await expect(page.getByText('17:00')).toBeVisible();
  });

  test('hiển thị trạng thái trống khi chưa có ca', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: EMPTY_PAGE })
    );
    await page.goto('/admin/shifts');

    await expect(page.getByText('Chưa có ca nào')).toBeVisible();
    await expect(page.getByText('Bấm "Tạo ca mới" để bắt đầu')).toBeVisible();
  });

  test('hiển thị badge số nhân viên theo ca', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/shifts');

    await expect(page.getByText('2 NV')).toBeVisible();
    await expect(page.getByText('0 NV')).toBeVisible();
  });

  // ── Create modal ───────────────────────────────────────────────────────

  test('nút "Tạo ca mới" mở modal tạo ca', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: /Tạo ca mới/ }).click();

    await expect(page.getByRole('heading', { name: 'Tạo ca mới' })).toBeVisible();
    await expect(page.locator('#name')).toBeVisible();
    await expect(page.locator('#startTime')).toBeVisible();
    await expect(page.locator('#endTime')).toBeVisible();
  });

  test('validate: tên ca không được để trống', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: /Tạo ca mới/ }).click();
    await page.getByRole('button', { name: 'Tạo ca', exact: true }).click();

    await expect(page.getByText('Vui lòng nhập tên ca')).toBeVisible();
  });

  test('validate: giờ bắt đầu phải nhỏ hơn giờ kết thúc', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: /Tạo ca mới/ }).click();
    await page.locator('#name').fill('Ca Test');
    await page.locator('#startTime').fill('17:00');
    await page.locator('#endTime').fill('08:00');
    await page.getByRole('button', { name: 'Tạo ca', exact: true }).click();

    await expect(page.getByText('Giờ bắt đầu phải nhỏ hơn giờ kết thúc')).toBeVisible();
  });

  test('tạo ca thành công → toast success', async ({ page }) => {
    const newShift = { ...MOCK_SHIFTS[0], id: 'shift-new', name: 'Ca Test' };
    await page.route('**/api/admin/shifts**', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, json: newShift });
      } else {
        await route.fulfill({ status: 200, json: MOCK_PAGE });
      }
    });
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: /Tạo ca mới/ }).click();
    await page.locator('#name').fill('Ca Test');
    await page.locator('#startTime').fill('08:00');
    await page.locator('#endTime').fill('17:00');
    await page.getByRole('button', { name: 'Tạo ca', exact: true }).click();

    await expect(page.getByText('Tạo ca thành công')).toBeVisible();
  });

  test('nút Hủy đóng modal tạo ca', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: /Tạo ca mới/ }).click();
    await expect(page.getByRole('heading', { name: 'Tạo ca mới' })).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByRole('heading', { name: 'Tạo ca mới' })).not.toBeVisible();
  });

  // ── Edit modal ─────────────────────────────────────────────────────────

  test('double-click hàng mở modal chỉnh sửa với dữ liệu sẵn có', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/shifts');

    await page.getByText('Ca Sáng').dblclick();

    await expect(page.getByRole('heading', { name: 'Chỉnh sửa ca' })).toBeVisible();
    await expect(page.locator('#name')).toHaveValue('Ca Sáng');
    await expect(page.locator('#startTime')).toHaveValue('08:00');
    await expect(page.locator('#endTime')).toHaveValue('17:00');
  });

  test('nút chỉnh sửa (✏️) mở modal edit', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: 'Chỉnh sửa ca Ca Sáng' }).click();
    await expect(page.getByRole('heading', { name: 'Chỉnh sửa ca' })).toBeVisible();
  });

  test('lưu chỉnh sửa ca thành công → toast success', async ({ page }) => {
    const updatedShift = { ...MOCK_SHIFTS[0], name: 'Ca Sáng (Cập nhật)' };
    await page.route('**/api/admin/shifts**', async (route) => {
      if (route.request().method() === 'PUT') {
        await route.fulfill({ status: 200, json: updatedShift });
      } else {
        await route.fulfill({ status: 200, json: MOCK_PAGE });
      }
    });
    await page.goto('/admin/shifts');

    await page.getByText('Ca Sáng').dblclick();
    await page.locator('#name').fill('Ca Sáng (Cập nhật)');
    await page.getByRole('button', { name: 'Lưu thay đổi' }).click();

    await expect(page.getByText('Cập nhật ca thành công')).toBeVisible();
  });

  // ── Delete flow ────────────────────────────────────────────────────────

  test('nút xóa mở dialog xác nhận', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: 'Xóa ca Ca Chiều' }).click();

    await expect(page.getByText('Xác nhận xóa ca')).toBeVisible();
    await expect(page.getByText('"Ca Chiều"')).toBeVisible();
  });

  test('xóa ca đang được gán → hiển thị lỗi SHIFT_IN_USE', async ({ page }) => {
    await page.route('**/api/admin/shifts**', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 409, json: { error: 'SHIFT_IN_USE', message: 'Shift is in use' } });
      } else {
        await route.fulfill({ status: 200, json: MOCK_PAGE });
      }
    });
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: 'Xóa ca Ca Sáng' }).click();
    await page.getByRole('button', { name: 'Xóa ca' }).last().click();

    await expect(page.getByText('Ca đang được gán cho nhân viên, không thể xóa')).toBeVisible();
  });

  test('xóa ca không được gán thành công → toast success', async ({ page }) => {
    await page.route('**/api/admin/shifts**', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 200, body: '' });
      } else {
        await route.fulfill({ status: 200, json: MOCK_PAGE });
      }
    });
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: 'Xóa ca Ca Chiều' }).click();
    await page.getByRole('button', { name: 'Xóa ca' }).last().click();

    await expect(page.getByText('Xóa ca thành công')).toBeVisible();
  });

  test('nút Hủy trong dialog đóng dialog không xóa', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: 'Xóa ca Ca Chiều' }).click();
    await expect(page.getByText('Xác nhận xóa ca')).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByText('Xác nhận xóa ca')).not.toBeVisible();
    await expect(page.getByText('Ca Chiều')).toBeVisible();
  });

  // ── Assign shift modal ─────────────────────────────────────────────────

  test('nút "Gán ca" mở modal gán ca cho nhân viên', async ({ page }) => {
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_PAGE })
    );
    await page.route('**/api/admin/employees**', route =>
      route.fulfill({ status: 200, json: MOCK_EMPLOYEES })
    );
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: 'Gán ca Ca Sáng' }).click();

    await expect(page.getByRole('heading', { name: 'Gán ca làm việc' })).toBeVisible();
    await expect(page.locator('#employeeSelect')).toBeVisible();
    await expect(page.locator('#employeeSelect')).toContainText('Nguyen Van A (emp1)');
    await expect(page.locator('#employeeSelect')).toContainText('Tran Thi B (emp2)');
  });

  test('gán ca thành công → toast success', async ({ page }) => {
    await page.route('**/api/admin/shifts**', async (route) => {
      if (route.request().method() === 'PUT' && route.request().url().includes('/assign/')) {
        await route.fulfill({ status: 200, json: MOCK_SHIFTS[0] });
      } else {
        await route.fulfill({ status: 200, json: MOCK_PAGE });
      }
    });
    await page.route('**/api/admin/employees**', route =>
      route.fulfill({ status: 200, json: MOCK_EMPLOYEES })
    );
    await page.goto('/admin/shifts');

    await page.getByRole('button', { name: 'Gán ca Ca Sáng' }).click();
    await page.locator('#employeeSelect').selectOption({ value: 'emp-1' });
    await page.getByRole('button', { name: 'Gán ca' }).last().click();

    await expect(page.getByText('Gán ca "Ca Sáng" thành công')).toBeVisible();
  });
});
