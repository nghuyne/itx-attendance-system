import { test, expect } from '@playwright/test';
import { seedAdminAuth } from '../support/auth';

// ── helpers ───────────────────────────────────────────────────────────────

const MOCK_USERS_PAGE = {
  content: [
    {
      id: 'user-1', username: 'emp1', email: 'emp1@itx.local', fullName: 'Nguyen Van A',
      role: 'EMPLOYEE', active: true, mustChangePassword: false,
      departmentId: 1, departmentName: 'Kỹ thuật', shiftId: 'shift-1', shiftName: 'Ca Sáng',
      createdAt: '2026-01-01T00:00:00',
    },
    {
      id: 'user-2', username: 'emp2', email: 'emp2@itx.local', fullName: 'Tran Thi B',
      role: 'LEADER', active: false, mustChangePassword: true,
      departmentId: null, departmentName: null, shiftId: null, shiftName: null,
      createdAt: '2026-01-02T00:00:00',
    },
  ],
  totalElements: 2, totalPages: 1, size: 20, number: 0,
};

const MOCK_DEPARTMENTS = [{ id: 1, name: 'Kỹ thuật', description: null, employeeCount: 1 }];
const MOCK_SHIFTS_PAGE = {
  content: [{ id: 'shift-1', name: 'Ca Sáng', startTime: '08:00', endTime: '17:00', assignedCount: 1 }],
  totalElements: 1, totalPages: 1, size: 100, number: 0,
};

// ── Admin User Account Management (Phase 3) ─────────────────────────────────

test.describe('Admin — Quản lý tài khoản nhân viên (Phase 3)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
    await page.route('**/api/admin/departments', route =>
      route.fulfill({ status: 200, json: MOCK_DEPARTMENTS })
    );
    await page.route('**/api/admin/shifts**', route =>
      route.fulfill({ status: 200, json: MOCK_SHIFTS_PAGE })
    );
  });

  // ── List ──────────────────────────────────────────────────────────────

  test('hiển thị danh sách tài khoản với vai trò và trạng thái', async ({ page }) => {
    await page.route('**/api/admin/users**', route =>
      route.fulfill({ status: 200, json: MOCK_USERS_PAGE })
    );
    await page.goto('/admin/users');

    await expect(page.getByRole('heading', { name: 'Tài khoản nhân viên' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Nguyen Van A' })).toBeVisible();
    await expect(page.getByText('Hoạt động')).toBeVisible();
    await expect(page.locator('span').filter({ hasText: 'Vô hiệu hóa' })).toBeVisible();
  });

  test('hiển thị trạng thái trống khi chưa có tài khoản nào', async ({ page }) => {
    await page.route('**/api/admin/users**', route =>
      route.fulfill({ status: 200, json: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 } })
    );
    await page.goto('/admin/users');

    await expect(page.getByText('Chưa có tài khoản nào')).toBeVisible();
  });

  test('hiển thị trạng thái lỗi khi API thất bại', async ({ page }) => {
    await page.route('**/api/admin/users**', route =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } })
    );
    await page.goto('/admin/users');

    await expect(page.getByText('Không thể tải danh sách tài khoản. Vui lòng thử lại.')).toBeVisible();
  });

  // ── Create ────────────────────────────────────────────────────────────

  test('tạo tài khoản mới thành công', async ({ page }) => {
    await page.route('**/api/admin/users**', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, json: MOCK_USERS_PAGE });
      } else if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 201,
          json: {
            id: 'user-3', username: 'newemp', email: 'newemp@itx.local', fullName: 'Nhan Vien Moi',
            role: 'EMPLOYEE', active: true, mustChangePassword: true,
            departmentId: null, departmentName: null, shiftId: null, shiftName: null,
            createdAt: '2026-01-03T00:00:00',
          },
        });
      }
    });
    await page.goto('/admin/users');

    await page.getByRole('button', { name: '+ Tạo tài khoản' }).click();
    await page.locator('#user-username').fill('newemp');
    await page.locator('#user-email').fill('newemp@itx.local');
    await page.locator('#user-fullname').fill('Nhan Vien Moi');
    await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();

    await expect(page.getByText('Tạo tài khoản thành công. Mật khẩu tạm thời đã được gửi qua email.')).toBeVisible();
  });

  test('tạo tài khoản trùng tên đăng nhập → toast lỗi', async ({ page }) => {
    await page.route('**/api/admin/users**', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, json: MOCK_USERS_PAGE });
      } else if (route.request().method() === 'POST') {
        await route.fulfill({ status: 409, json: { error: 'USERNAME_ALREADY_EXISTS' } });
      }
    });
    await page.goto('/admin/users');

    await page.getByRole('button', { name: '+ Tạo tài khoản' }).click();
    await page.locator('#user-username').fill('emp1');
    await page.locator('#user-email').fill('emp1dup@itx.local');
    await page.locator('#user-fullname').fill('Trung Ten');
    await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();

    await expect(page.getByText('Tên đăng nhập đã tồn tại')).toBeVisible();
  });

  test('nút submit báo lỗi khi để trống các trường bắt buộc', async ({ page }) => {
    await page.route('**/api/admin/users**', route =>
      route.fulfill({ status: 200, json: MOCK_USERS_PAGE })
    );
    await page.goto('/admin/users');

    await page.getByRole('button', { name: '+ Tạo tài khoản' }).click();
    await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();

    await expect(page.getByText('Vui lòng nhập họ tên')).toBeVisible();
  });

  // ── Reset password ────────────────────────────────────────────────────

  test('đặt lại mật khẩu thành công', async ({ page }) => {
    await page.route('**/api/admin/users**', route =>
      route.fulfill({ status: 200, json: MOCK_USERS_PAGE })
    );
    await page.route('**/api/admin/users/user-1/reset-password', route =>
      route.fulfill({ status: 200 })
    );
    await page.goto('/admin/users');

    await page.getByRole('row', { name: /Nguyen Van A/ }).getByRole('button', { name: 'Đặt lại MK' }).click();
    await page.getByRole('button', { name: 'Đặt lại mật khẩu', exact: true }).click();

    await expect(page.getByText('Đã đặt lại mật khẩu, email đã được gửi')).toBeVisible();
  });

  // ── Deactivate / Activate ─────────────────────────────────────────────

  test('vô hiệu hóa tài khoản đang hoạt động', async ({ page }) => {
    await page.route('**/api/admin/users**', route =>
      route.fulfill({ status: 200, json: MOCK_USERS_PAGE })
    );
    await page.route('**/api/admin/users/user-1/deactivate', route =>
      route.fulfill({ status: 200, json: { ...MOCK_USERS_PAGE.content[0], active: false } })
    );
    await page.goto('/admin/users');

    await page.getByRole('row', { name: /Nguyen Van A/ }).getByRole('button', { name: 'Vô hiệu hóa' }).click();

    await expect(page.getByText('Cập nhật trạng thái tài khoản thành công')).toBeVisible();
  });

  test('kích hoạt lại tài khoản đã vô hiệu hóa', async ({ page }) => {
    await page.route('**/api/admin/users**', route =>
      route.fulfill({ status: 200, json: MOCK_USERS_PAGE })
    );
    await page.route('**/api/admin/users/user-2/activate', route =>
      route.fulfill({ status: 200, json: { ...MOCK_USERS_PAGE.content[1], active: true } })
    );
    await page.goto('/admin/users');

    await page.getByRole('row', { name: /Tran Thi B/ }).getByRole('button', { name: 'Kích hoạt' }).click();

    await expect(page.getByText('Cập nhật trạng thái tài khoản thành công')).toBeVisible();
  });

  test('tự vô hiệu hóa tài khoản của chính mình → toast lỗi', async ({ page }) => {
    const selfUserPage = {
      ...MOCK_USERS_PAGE,
      content: [{ ...MOCK_USERS_PAGE.content[0], id: 'admin-id', fullName: 'System Administrator' }],
    };
    await page.route('**/api/admin/users**', route =>
      route.fulfill({ status: 200, json: selfUserPage })
    );
    await page.route('**/api/admin/users/admin-id/deactivate', route =>
      route.fulfill({ status: 400, json: { error: 'CANNOT_DEACTIVATE_SELF' } })
    );
    await page.goto('/admin/users');

    await page.getByRole('row', { name: /System Administrator/ }).getByRole('button', { name: 'Vô hiệu hóa' }).click();

    await expect(page.getByText('Không thể tự vô hiệu hóa tài khoản của chính mình')).toBeVisible();
  });
});
