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

const MOCK_IPS = [
  { id: 1, ipAddress: '203.0.113.10', scope: 'COMPANY', employeeId: null, employeeName: null, description: 'Văn phòng HCM', createdAt: '2026-06-01T08:00:00' },
  { id: 2, ipAddress: '198.51.100.5', scope: 'INDIVIDUAL', employeeId: 'emp-1', employeeName: 'Nguyen Van A', description: null, createdAt: '2026-06-02T09:00:00' },
];

const MOCK_IP_PAGE = { content: MOCK_IPS, totalElements: 2, totalPages: 1, size: 100, number: 0 };
const EMPTY_IP_PAGE = { content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 };

const MOCK_EMPLOYEES = [
  { id: 'emp-1', username: 'emp1', fullName: 'Nguyen Van A' },
  { id: 'emp-2', username: 'emp2', fullName: 'Tran Thi B' },
];

// ── Valid IP Management ───────────────────────────────────────────────────

test.describe('Admin — Quản lý IP hợp lệ (Story 2.2)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
  });

  // ── List & empty state ─────────────────────────────────────────────────

  test('hiển thị danh sách IP với đủ cột', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: MOCK_IP_PAGE })
    );
    await page.goto('/admin/ips');

    await expect(page.getByRole('heading', { name: 'Quản lý IP hợp lệ' })).toBeVisible();
    await expect(page.getByText('203.0.113.10')).toBeVisible();
    await expect(page.getByText('198.51.100.5')).toBeVisible();
    await expect(page.getByText('Toàn công ty')).toBeVisible();
    await expect(page.getByText('Cá nhân')).toBeVisible();
    await expect(page.getByText('Văn phòng HCM')).toBeVisible();
    await expect(page.getByText('Nguyen Van A')).toBeVisible();
  });

  test('hiển thị trạng thái trống khi chưa có IP', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: EMPTY_IP_PAGE })
    );
    await page.goto('/admin/ips');

    await expect(page.getByText('Chưa có IP nào được cấu hình')).toBeVisible();
    await expect(page.getByText('Bấm "+ Thêm IP" để thêm IP văn phòng đầu tiên')).toBeVisible();
  });

  // ── Add IP modal ───────────────────────────────────────────────────────

  test('nút "+ Thêm IP" mở modal', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: MOCK_IP_PAGE })
    );
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: /Thêm IP/ }).click();

    await expect(page.getByRole('heading', { name: 'Thêm IP hợp lệ' })).toBeVisible();
    await expect(page.locator('#ipAddress')).toBeVisible();
  });

  test('validate: địa chỉ IP không được để trống', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: MOCK_IP_PAGE })
    );
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: /Thêm IP/ }).click();
    await page.getByRole('button', { name: 'Lưu IP' }).click();

    await expect(page.getByText('Vui lòng nhập địa chỉ IP')).toBeVisible();
  });

  test('validate: định dạng IP không hợp lệ', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: MOCK_IP_PAGE })
    );
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: /Thêm IP/ }).click();
    await page.locator('#ipAddress').fill('not-an-ip-address');
    await page.getByRole('button', { name: 'Lưu IP' }).click();

    await expect(page.getByText('Định dạng IP không hợp lệ')).toBeVisible();
  });

  test('phạm vi INDIVIDUAL hiển thị dropdown chọn nhân viên', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: MOCK_IP_PAGE })
    );
    await page.route('**/api/admin/employees**', route =>
      route.fulfill({ status: 200, json: MOCK_EMPLOYEES })
    );
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: /Thêm IP/ }).click();
    await expect(page.locator('#employeeId')).not.toBeVisible();

    await page.getByRole('radio', { name: 'Cá nhân' }).click();
    await expect(page.locator('#employeeId')).toBeVisible();
    await expect(page.locator('#employeeId')).toContainText('Nguyen Van A');
    await expect(page.locator('#employeeId')).toContainText('Tran Thi B');
  });

  test('validate: INDIVIDUAL scope mà không chọn nhân viên → lỗi', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: MOCK_IP_PAGE })
    );
    await page.route('**/api/admin/employees**', route =>
      route.fulfill({ status: 200, json: MOCK_EMPLOYEES })
    );
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: /Thêm IP/ }).click();
    await page.locator('#ipAddress').fill('10.0.0.1');
    await page.getByRole('radio', { name: 'Cá nhân' }).click();
    await page.getByRole('button', { name: 'Lưu IP' }).click();

    await expect(page.getByText('Vui lòng chọn nhân viên')).toBeVisible();
  });

  test('thêm IP cấp COMPANY thành công → toast success', async ({ page }) => {
    const newIp = { id: 3, ipAddress: '10.0.0.1', scope: 'COMPANY', employeeId: null, employeeName: null, description: 'Test', createdAt: '2026-06-30T08:00:00' };
    await page.route('**/api/admin/valid-ips**', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, json: newIp });
      } else {
        await route.fulfill({ status: 200, json: MOCK_IP_PAGE });
      }
    });
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: /Thêm IP/ }).click();
    await page.locator('#ipAddress').fill('10.0.0.1');
    await page.locator('#description').fill('Test office');
    await page.getByRole('button', { name: 'Lưu IP' }).click();

    await expect(page.getByText('Thêm IP thành công')).toBeVisible();
  });

  test('thêm IP cấp INDIVIDUAL thành công → toast success', async ({ page }) => {
    const newIp = { id: 4, ipAddress: '192.168.1.1', scope: 'INDIVIDUAL', employeeId: 'emp-2', employeeName: 'Tran Thi B', description: null, createdAt: '2026-06-30T08:00:00' };
    await page.route('**/api/admin/valid-ips**', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, json: newIp });
      } else {
        await route.fulfill({ status: 200, json: MOCK_IP_PAGE });
      }
    });
    await page.route('**/api/admin/employees**', route =>
      route.fulfill({ status: 200, json: MOCK_EMPLOYEES })
    );
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: /Thêm IP/ }).click();
    await page.locator('#ipAddress').fill('192.168.1.1');
    await page.getByRole('radio', { name: 'Cá nhân' }).click();
    await page.locator('#employeeId').selectOption({ value: 'emp-2' });
    await page.getByRole('button', { name: 'Lưu IP' }).click();

    await expect(page.getByText('Thêm IP thành công')).toBeVisible();
  });

  test('thêm IP trùng lặp → toast lỗi DUPLICATE_IP', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 409, json: { error: 'DUPLICATE_IP', message: 'IP already exists' } });
      } else {
        await route.fulfill({ status: 200, json: MOCK_IP_PAGE });
      }
    });
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: /Thêm IP/ }).click();
    await page.locator('#ipAddress').fill('203.0.113.10');
    await page.getByRole('button', { name: 'Lưu IP' }).click();

    await expect(page.getByText('IP này đã tồn tại trong hệ thống')).toBeVisible();
  });

  test('validate: IPv6 hợp lệ được chấp nhận', async ({ page }) => {
    const newIp = { id: 5, ipAddress: '2001:db8::1', scope: 'COMPANY', employeeId: null, employeeName: null, description: null, createdAt: '2026-06-30T08:00:00' };
    await page.route('**/api/admin/valid-ips**', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, json: newIp });
      } else {
        await route.fulfill({ status: 200, json: MOCK_IP_PAGE });
      }
    });
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: /Thêm IP/ }).click();
    await page.locator('#ipAddress').fill('2001:db8::1');
    await page.getByRole('button', { name: 'Lưu IP' }).click();

    await expect(page.getByText('Thêm IP thành công')).toBeVisible();
  });

  test('nút Hủy đóng modal không lưu', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: MOCK_IP_PAGE })
    );
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: /Thêm IP/ }).click();
    await expect(page.getByRole('heading', { name: 'Thêm IP hợp lệ' })).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByRole('heading', { name: 'Thêm IP hợp lệ' })).not.toBeVisible();
  });

  // ── Delete flow ────────────────────────────────────────────────────────

  test('nút xóa mở dialog xác nhận xóa IP', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: MOCK_IP_PAGE })
    );
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: 'Xóa IP 203.0.113.10' }).click();

    await expect(page.getByText('Xác nhận xóa IP')).toBeVisible();
    await expect(page.locator('strong.font-mono', { hasText: '203.0.113.10' })).toBeVisible();
  });

  test('xóa IP thành công → toast success', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 200, body: '' });
      } else {
        await route.fulfill({ status: 200, json: MOCK_IP_PAGE });
      }
    });
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: 'Xóa IP 203.0.113.10' }).click();
    await page.getByRole('button', { name: 'Xóa IP' }).last().click();

    await expect(page.getByText('Xóa IP thành công')).toBeVisible();
  });

  test('nút Hủy trong dialog xóa không xóa IP', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: MOCK_IP_PAGE })
    );
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: 'Xóa IP 203.0.113.10' }).click();
    await expect(page.getByText('Xác nhận xóa IP')).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByText('Xác nhận xóa IP')).not.toBeVisible();
    await expect(page.getByText('203.0.113.10')).toBeVisible();
  });

  test('xóa IP cấp INDIVIDUAL hiển thị tên nhân viên trong dialog', async ({ page }) => {
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: MOCK_IP_PAGE })
    );
    await page.goto('/admin/ips');

    await page.getByRole('button', { name: 'Xóa IP 198.51.100.5' }).click();

    await expect(page.locator('strong.font-mono', { hasText: '198.51.100.5' })).toBeVisible();
    await expect(page.getByText(/nhân viên: Nguyen Van A/)).toBeVisible();
  });
});
