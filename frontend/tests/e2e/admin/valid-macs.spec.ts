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

const MOCK_MACS = [
  { id: 1, bssid: 'AA:BB:CC:DD:EE:FF', description: 'Router tầng 2', createdBy: 'admin', createdAt: '2026-06-29T08:00:00' },
  { id: 2, bssid: '11:22:33:44:55:66', description: null, createdBy: 'admin', createdAt: '2026-06-29T09:00:00' },
];

const EMPTY_MACS: typeof MOCK_MACS = [];

// ── Admin MACs Management ────────────────────────────────────────────────

test.describe('Admin — Quản lý MAC (BSSID) (Story 10.4)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
  });

  // ── List & empty state ─────────────────────────────────────────────────

  test('hiển thị danh sách BSSID với đủ cột', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', route =>
      route.fulfill({ status: 200, json: MOCK_MACS })
    );
    await page.goto('/admin/macs');

    await expect(page.getByRole('heading', { name: 'Quản lý MAC (BSSID)' })).toBeVisible();
    await expect(page.getByText('AA:BB:CC:DD:EE:FF')).toBeVisible();
    await expect(page.getByText('11:22:33:44:55:66')).toBeVisible();
    await expect(page.getByText('Router tầng 2')).toBeVisible();
  });

  test('hiển thị trạng thái trống khi chưa có BSSID', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', route =>
      route.fulfill({ status: 200, json: EMPTY_MACS })
    );
    await page.goto('/admin/macs');

    await expect(page.getByText('Chưa có BSSID nào được cấu hình')).toBeVisible();
    await expect(page.getByText('Bấm "+ Thêm BSSID" để thêm router Wi-Fi văn phòng đầu tiên')).toBeVisible();
  });

  test('trạng thái lỗi khi tải danh sách thất bại', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', route =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } })
    );
    await page.goto('/admin/macs');

    await expect(page.getByText('Không thể tải danh sách BSSID. Vui lòng thử lại.')).toBeVisible();
  });

  // ── Add BSSID modal ───────────────────────────────────────────────────

  test('nút "+ Thêm BSSID" mở modal', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', route =>
      route.fulfill({ status: 200, json: MOCK_MACS })
    );
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: /Thêm BSSID/ }).click();

    await expect(page.getByRole('heading', { name: 'Thêm BSSID hợp lệ' })).toBeVisible();
    await expect(page.locator('#bssid')).toBeVisible();
    await expect(page.locator('#description')).toBeVisible();
  });

  test('validate: BSSID không được để trống', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', route =>
      route.fulfill({ status: 200, json: MOCK_MACS })
    );
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: /Thêm BSSID/ }).click();
    await page.getByRole('button', { name: 'Lưu BSSID' }).click();

    await expect(page.getByText('Vui lòng nhập BSSID')).toBeVisible();
  });

  test('validate: định dạng BSSID không hợp lệ', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', route =>
      route.fulfill({ status: 200, json: MOCK_MACS })
    );
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: /Thêm BSSID/ }).click();
    await page.locator('#bssid').fill('not-a-bssid');
    await page.getByRole('button', { name: 'Lưu BSSID' }).click();

    await expect(page.getByText('Định dạng BSSID không hợp lệ (ví dụ: AA:BB:CC:DD:EE:FF)')).toBeVisible();
  });

  test('thêm BSSID thành công → toast success', async ({ page }) => {
    const newMac = { id: 3, bssid: '10:20:30:40:50:60', description: 'Test AP', createdBy: 'admin', createdAt: '2026-07-02T08:00:00' };
    await page.route('**/api/admin/valid-macs', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, json: newMac });
      } else {
        await route.fulfill({ status: 200, json: MOCK_MACS });
      }
    });
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: /Thêm BSSID/ }).click();
    await page.locator('#bssid').fill('10:20:30:40:50:60');
    await page.locator('#description').fill('Test AP');
    await page.getByRole('button', { name: 'Lưu BSSID' }).click();

    await expect(page.getByText('Thêm BSSID thành công')).toBeVisible();
  });

  test('gửi BSSID viết thường được tự động uppercase trước khi submit', async ({ page }) => {
    let submittedBody: { bssid?: string } = {};
    await page.route('**/api/admin/valid-macs', async (route) => {
      if (route.request().method() === 'POST') {
        submittedBody = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          json: { id: 4, bssid: 'AA:BB:CC:11:22:33', description: null, createdBy: 'admin', createdAt: '2026-07-02T08:00:00' },
        });
      } else {
        await route.fulfill({ status: 200, json: MOCK_MACS });
      }
    });
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: /Thêm BSSID/ }).click();
    await page.locator('#bssid').fill('aa:bb:cc:11:22:33');
    await page.getByRole('button', { name: 'Lưu BSSID' }).click();

    await expect(page.getByText('Thêm BSSID thành công')).toBeVisible();
    expect(submittedBody.bssid).toBe('AA:BB:CC:11:22:33');
  });

  test('thêm BSSID trùng lặp → toast lỗi DUPLICATE_MAC', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 409, json: { error: 'DUPLICATE_MAC', message: 'BSSID already exists' } });
      } else {
        await route.fulfill({ status: 200, json: MOCK_MACS });
      }
    });
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: /Thêm BSSID/ }).click();
    await page.locator('#bssid').fill('AA:BB:CC:DD:EE:FF');
    await page.getByRole('button', { name: 'Lưu BSSID' }).click();

    await expect(page.getByText('BSSID này đã tồn tại trong hệ thống')).toBeVisible();
  });

  test('thêm BSSID thất bại (lỗi khác) → toast lỗi chung', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } });
      } else {
        await route.fulfill({ status: 200, json: MOCK_MACS });
      }
    });
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: /Thêm BSSID/ }).click();
    await page.locator('#bssid').fill('AA:BB:CC:DD:EE:FF');
    await page.getByRole('button', { name: 'Lưu BSSID' }).click();

    await expect(page.getByText('Có lỗi xảy ra, vui lòng thử lại')).toBeVisible();
  });

  test('nút Hủy đóng modal không lưu', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', route =>
      route.fulfill({ status: 200, json: MOCK_MACS })
    );
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: /Thêm BSSID/ }).click();
    await expect(page.getByRole('heading', { name: 'Thêm BSSID hợp lệ' })).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByRole('heading', { name: 'Thêm BSSID hợp lệ' })).not.toBeVisible();
  });

  // ── Delete flow ────────────────────────────────────────────────────────

  test('nút xóa mở dialog xác nhận xóa BSSID', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', route =>
      route.fulfill({ status: 200, json: MOCK_MACS })
    );
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: 'Xóa BSSID AA:BB:CC:DD:EE:FF' }).click();

    await expect(page.getByText('Xác nhận xóa BSSID')).toBeVisible();
    await expect(page.locator('strong.font-mono', { hasText: 'AA:BB:CC:DD:EE:FF' })).toBeVisible();
  });

  test('xóa BSSID thành công → toast success', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', async (route) => {
      await route.fulfill({ status: 200, json: MOCK_MACS });
    });
    await page.route('**/api/admin/valid-macs/1', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 204, body: '' });
      }
    });
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: 'Xóa BSSID AA:BB:CC:DD:EE:FF' }).click();
    await page.getByRole('button', { name: 'Xóa BSSID' }).last().click();

    await expect(page.getByText('Xóa BSSID thành công')).toBeVisible();
  });

  test('nút Hủy trong dialog xóa không xóa BSSID', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', route =>
      route.fulfill({ status: 200, json: MOCK_MACS })
    );
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: 'Xóa BSSID AA:BB:CC:DD:EE:FF' }).click();
    await expect(page.getByText('Xác nhận xóa BSSID')).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByText('Xác nhận xóa BSSID')).not.toBeVisible();
    await expect(page.getByText('AA:BB:CC:DD:EE:FF')).toBeVisible();
  });

  test('xóa BSSID thất bại (API 500) → toast lỗi', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', async (route) => {
      await route.fulfill({ status: 200, json: MOCK_MACS });
    });
    await page.route('**/api/admin/valid-macs/1', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } });
      }
    });
    await page.goto('/admin/macs');

    await page.getByRole('button', { name: 'Xóa BSSID AA:BB:CC:DD:EE:FF' }).click();
    await page.getByRole('button', { name: 'Xóa BSSID' }).last().click();

    await expect(page.getByText('Xóa BSSID thất bại, vui lòng thử lại')).toBeVisible();
  });

  // ── Navigation ─────────────────────────────────────────────────────────

  test('sidebar chứa link "Quản lý MAC" điều hướng đến /admin/macs', async ({ page }) => {
    await page.route('**/api/admin/valid-macs', route =>
      route.fulfill({ status: 200, json: EMPTY_MACS })
    );
    await page.route('**/api/admin/valid-ips**', route =>
      route.fulfill({ status: 200, json: { content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 } })
    );
    await page.goto('/admin/ips');

    await page.getByRole('link', { name: /Quản lý MAC/ }).click();
    await expect(page).toHaveURL(/\/admin\/macs$/);
    await expect(page.getByRole('heading', { name: 'Quản lý MAC (BSSID)' })).toBeVisible();
  });
});
