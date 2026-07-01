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

const MOCK_DEPARTMENTS = [
  { id: 1, name: 'Kỹ thuật', description: 'Phòng kỹ thuật', employeeCount: 2 },
  { id: 2, name: 'Kinh doanh', description: null, employeeCount: 0 },
];

const MOCK_EMPLOYEES = [
  { id: 'emp-1', fullName: 'Nguyen Van A', username: 'emp1', shiftId: 'shift-1', shiftName: 'Ca Sáng', departmentId: 1, departmentName: 'Kỹ thuật' },
  { id: 'emp-2', fullName: 'Tran Thi B', username: 'emp2', shiftId: null, shiftName: null, departmentId: null, departmentName: null },
];

const MOCK_SHIFTS_PAGE = {
  content: [{ id: 'shift-1', name: 'Ca Sáng', startTime: '08:00', endTime: '17:00', assignedCount: 2 }],
  totalElements: 1, totalPages: 1, size: 100, number: 0,
};

// ── Admin Department Management (Story 9.1) ────────────────────────────────

test.describe('Admin — Quản lý phòng ban & gán ca hàng loạt (Story 9.1)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.route('**/api/admin/employees/details', route =>
      route.fulfill({ status: 200, json: MOCK_EMPLOYEES })
    );
  });

  // ── List ──────────────────────────────────────────────────────────────

  test('hiển thị danh sách phòng ban với số nhân viên', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.goto('/admin/departments');

    await expect(page.getByRole('heading', { name: 'Phòng ban' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Kỹ thuật', exact: true }).first()).toBeVisible();
    await expect(page.getByText('2 NV')).toBeVisible();
    await expect(page.getByText('0 NV')).toBeVisible();
  });

  test('hiển thị danh sách nhân viên với ca và phòng ban', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.goto('/admin/departments');

    await expect(page.getByRole('cell', { name: 'Nguyen Van A' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Ca Sáng' })).toBeVisible();
  });

  test('hiển thị trạng thái trống khi chưa có phòng ban nào', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: [] })
    );
    await page.goto('/admin/departments');

    await expect(page.getByText('Chưa có phòng ban nào')).toBeVisible();
  });

  test('hiển thị trạng thái lỗi khi API thất bại', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } })
    );
    await page.goto('/admin/departments');

    await expect(page.getByText('Không thể tải danh sách phòng ban. Vui lòng thử lại.')).toBeVisible();
  });

  // ── Create ────────────────────────────────────────────────────────────

  test('tạo phòng ban mới thành công', async ({ page }) => {
    await page.route('**/api/admin/departments', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, json: MOCK_DEPARTMENTS });
      } else if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, json: { id: 3, name: 'Nhân sự', description: null, employeeCount: 0 } });
      }
    });
    await page.goto('/admin/departments');

    await page.getByRole('button', { name: '+ Tạo phòng ban' }).click();
    await page.locator('#dept-name').fill('Nhân sự');
    await page.getByRole('button', { name: 'Tạo phòng ban', exact: true }).click();

    await expect(page.getByText('Tạo phòng ban thành công')).toBeVisible();
  });

  test('tạo phòng ban trùng tên → toast lỗi', async ({ page }) => {
    await page.route('**/api/admin/departments', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, json: MOCK_DEPARTMENTS });
      } else if (route.request().method() === 'POST') {
        await route.fulfill({ status: 409, json: { error: 'DEPARTMENT_ALREADY_EXISTS' } });
      }
    });
    await page.goto('/admin/departments');

    await page.getByRole('button', { name: '+ Tạo phòng ban' }).click();
    await page.locator('#dept-name').fill('Kỹ thuật');
    await page.getByRole('button', { name: 'Tạo phòng ban', exact: true }).click();

    await expect(page.getByText('Tên phòng ban đã tồn tại')).toBeVisible();
  });

  test('nút submit bị disabled và báo lỗi khi để trống tên', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.goto('/admin/departments');

    await page.getByRole('button', { name: '+ Tạo phòng ban' }).click();
    await page.getByRole('button', { name: 'Tạo phòng ban', exact: true }).click();

    await expect(page.getByText('Vui lòng nhập tên phòng ban')).toBeVisible();
  });

  // ── Edit ──────────────────────────────────────────────────────────────

  test('sửa phòng ban điền sẵn dữ liệu hiện có và lưu thành công', async ({ page }) => {
    await page.route('**/api/admin/departments', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, json: MOCK_DEPARTMENTS });
      }
    });
    await page.route('**/api/admin/departments/1', async route => {
      if (route.request().method() === 'PUT') {
        await route.fulfill({ status: 200, json: { id: 1, name: 'Kỹ thuật (Mới)', description: 'Phòng kỹ thuật', employeeCount: 2 } });
      }
    });
    await page.goto('/admin/departments');

    await page.getByRole('button', { name: 'Sửa Kỹ thuật' }).click();
    await expect(page.locator('#dept-name')).toHaveValue('Kỹ thuật');

    await page.locator('#dept-name').fill('Kỹ thuật (Mới)');
    await page.getByRole('button', { name: 'Lưu thay đổi' }).click();

    await expect(page.getByText('Cập nhật phòng ban thành công')).toBeVisible();
  });

  // ── Delete ────────────────────────────────────────────────────────────

  test('xóa phòng ban còn nhân viên → toast lỗi DEPARTMENT_HAS_EMPLOYEES', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.route('**/api/admin/departments/1', route =>
      route.fulfill({ status: 400, json: { error: 'DEPARTMENT_HAS_EMPLOYEES' } })
    );
    await page.goto('/admin/departments');

    await page.getByRole('button', { name: 'Xóa Kỹ thuật' }).click();
    await page.getByRole('button', { name: 'Xóa', exact: true }).click();

    await expect(page.getByText('Phòng ban còn nhân viên, không thể xóa')).toBeVisible();
  });

  test('xóa phòng ban trống thành công', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.route('**/api/admin/departments/2', route =>
      route.fulfill({ status: 204 })
    );
    await page.goto('/admin/departments');

    await page.getByRole('button', { name: 'Xóa Kinh doanh' }).click();
    await page.getByRole('button', { name: 'Xóa', exact: true }).click();

    await expect(page.getByText('Xóa phòng ban thành công')).toBeVisible();
  });

  test('nút Hủy đóng hộp thoại xác nhận xóa mà không gọi API', async ({ page }) => {
    let deleteCalled = false;
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.route('**/api/admin/departments/1', async route => {
      deleteCalled = true;
      await route.fulfill({ status: 204 });
    });
    await page.goto('/admin/departments');

    await page.getByRole('button', { name: 'Xóa Kỹ thuật' }).click();
    await expect(page.getByText('Xác nhận xóa phòng ban')).toBeVisible();
    await page.getByRole('button', { name: 'Hủy' }).click();

    await expect(page.getByText('Xác nhận xóa phòng ban')).not.toBeVisible();
    expect(deleteCalled).toBe(false);
  });

  // ── Bulk shift assign ─────────────────────────────────────────────────

  test('gán ca hàng loạt thành công hiển thị số lượng nhân viên đã cập nhật', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_SHIFTS_PAGE })
    );
    await page.route('**/api/admin/departments/1/shift', route =>
      route.fulfill({ status: 200, json: { updatedCount: 2 } })
    );
    await page.goto('/admin/departments');

    await page.getByRole('row', { name: /Kỹ thuật/ }).getByRole('button', { name: 'Gán ca' }).click();
    await page.locator('#shiftSelect').selectOption({ value: 'shift-1' });
    await page.getByRole('button', { name: 'Gán ca' }).last().click();

    await expect(page.getByText('Đã gán ca cho 2 nhân viên')).toBeVisible();
  });

  test('gán ca cho phòng ban trống → toast lỗi DEPARTMENT_EMPTY', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_SHIFTS_PAGE })
    );
    await page.route('**/api/admin/departments/2/shift', route =>
      route.fulfill({ status: 400, json: { error: 'DEPARTMENT_EMPTY' } })
    );
    await page.goto('/admin/departments');

    await page.getByRole('row', { name: /Kinh doanh/ }).getByRole('button', { name: 'Gán ca' }).click();
    await page.locator('#shiftSelect').selectOption({ value: 'shift-1' });
    await page.getByRole('button', { name: 'Gán ca' }).last().click();

    await expect(page.getByText('Phòng ban không có nhân viên để gán ca')).toBeVisible();
  });

  test('nút Gán ca bị disabled khi chưa chọn ca', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_SHIFTS_PAGE })
    );
    await page.goto('/admin/departments');

    await page.getByRole('row', { name: /Kỹ thuật/ }).getByRole('button', { name: 'Gán ca' }).click();

    await expect(page.getByRole('button', { name: 'Gán ca' }).last()).toBeDisabled();
  });

  // ── Change employee department ────────────────────────────────────────

  test('đổi phòng ban cho nhân viên thành công', async ({ page }) => {
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.route('**/api/admin/employees/emp-2/department', route =>
      route.fulfill({ status: 200, json: { ...MOCK_EMPLOYEES[1], departmentId: 2, departmentName: 'Kinh doanh' } })
    );
    await page.goto('/admin/departments');

    await page.getByRole('row', { name: /Tran Thi B/ }).getByRole('button', { name: 'Đổi phòng ban' }).click();
    await page.locator('#deptSelect').selectOption({ value: '2' });
    await page.getByRole('button', { name: 'Lưu' }).click();

    await expect(page.getByText('Cập nhật phòng ban thành công')).toBeVisible();
  });
});
