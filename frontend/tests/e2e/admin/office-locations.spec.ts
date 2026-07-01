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

const MOCK_LOCATIONS = [
  { id: 1, name: 'Văn phòng chính', latitude: 10.77695, longitude: 106.7007, radiusMeters: 200, active: true, createdAt: '2026-06-18T08:00:00' },
  { id: 2, name: 'Văn phòng chi nhánh', latitude: 10.8, longitude: 106.75, radiusMeters: 300, active: false, createdAt: '2026-06-19T08:00:00' },
];

const EMPTY_LOCATIONS: typeof MOCK_LOCATIONS = [];

// ── GPS Office Radius Validation — Admin UI (Story 7.1) ─────────────────────

test.describe('Admin — Vị trí văn phòng (Story 7.1)', () => {
  test.beforeEach(async ({ page }) => {
    await seedAdminAuth(page);
    await page.route('**/api/notifications/pending', route =>
      route.fulfill({ status: 200, json: { notifications: [], unreadCount: 0 } })
    );
  });

  // ── List & empty state ─────────────────────────────────────────────────

  test('hiển thị danh sách vị trí với đủ cột', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await expect(page.getByRole('heading', { name: 'Vị trí văn phòng' })).toBeVisible();
    await expect(page.getByText('Văn phòng chính')).toBeVisible();
    await expect(page.getByText('Văn phòng chi nhánh')).toBeVisible();
    await expect(page.getByText('200')).toBeVisible();
    await expect(page.getByText('Hoạt động')).toBeVisible();
    await expect(page.getByText('Tắt')).toBeVisible();
  });

  test('hiển thị trạng thái trống khi chưa có vị trí nào', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: EMPTY_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await expect(page.getByText('Chưa có vị trí văn phòng nào')).toBeVisible();
    await expect(page.getByText('Bấm "+ Thêm vị trí" để thêm vị trí đầu tiên')).toBeVisible();
  });

  test('hiển thị lỗi khi tải danh sách thất bại', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } })
    );
    await page.goto('/admin/office-locations');

    await expect(page.getByText('Không thể tải danh sách vị trí. Vui lòng thử lại.')).toBeVisible();
  });

  // ── Add location modal ───────────────────────────────────────────────────

  test('nút "+ Thêm vị trí" mở modal thêm mới', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: '+ Thêm vị trí' }).click();

    await expect(page.getByRole('heading', { name: 'Thêm vị trí văn phòng' })).toBeVisible();
    await expect(page.locator('#name')).toBeVisible();
    await expect(page.locator('#latitude')).toBeVisible();
    await expect(page.locator('#longitude')).toBeVisible();
    await expect(page.locator('#radiusMeters')).toHaveValue('200');
  });

  test('validate: tên vị trí không được để trống', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: '+ Thêm vị trí' }).click();
    await page.locator('#latitude').fill('10.77');
    await page.locator('#longitude').fill('106.70');
    await page.getByRole('button', { name: 'Thêm vị trí' }).last().click();

    await expect(page.getByText('Vui lòng nhập tên vị trí')).toBeVisible();
  });

  test('validate: bán kính dưới 50m bị từ chối', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: '+ Thêm vị trí' }).click();
    await page.locator('#name').fill('VP Test');
    await page.locator('#latitude').fill('10.77');
    await page.locator('#longitude').fill('106.70');
    await page.locator('#radiusMeters').fill('10');
    await page.getByRole('button', { name: 'Thêm vị trí' }).last().click();

    await expect(page.getByText('Tối thiểu 50m')).toBeVisible();
  });

  test('validate: bán kính trên 5000m bị từ chối', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: '+ Thêm vị trí' }).click();
    await page.locator('#name').fill('VP Test');
    await page.locator('#latitude').fill('10.77');
    await page.locator('#longitude').fill('106.70');
    await page.locator('#radiusMeters').fill('9999');
    await page.getByRole('button', { name: 'Thêm vị trí' }).last().click();

    await expect(page.getByText('Tối đa 5000m')).toBeVisible();
  });

  test('validate: vĩ độ ngoài khoảng -90..90 bị từ chối', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: '+ Thêm vị trí' }).click();
    await page.locator('#name').fill('VP Test');
    await page.locator('#latitude').fill('120');
    await page.locator('#longitude').fill('106.70');
    await page.getByRole('button', { name: 'Thêm vị trí' }).last().click();

    await expect(page.getByRole('heading', { name: 'Thêm vị trí văn phòng' })).toBeVisible();
  });

  test('thêm vị trí thành công → toast success', async ({ page }) => {
    const newLocation = { id: 3, name: 'VP Mới', latitude: 10.77, longitude: 106.7, radiusMeters: 150, active: true, createdAt: '2026-07-01T08:00:00' };
    await page.route('**/api/admin/office-locations', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, json: newLocation });
      } else {
        await route.fulfill({ status: 200, json: MOCK_LOCATIONS });
      }
    });
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: '+ Thêm vị trí' }).click();
    await page.locator('#name').fill('VP Mới');
    await page.locator('#latitude').fill('10.77');
    await page.locator('#longitude').fill('106.70');
    await page.locator('#radiusMeters').fill('150');
    await page.getByRole('button', { name: 'Thêm vị trí' }).last().click();

    await expect(page.getByText('Thêm vị trí thành công')).toBeVisible();
  });

  test('thêm vị trí thất bại (bán kính không hợp lệ từ server) → toast lỗi', async ({ page }) => {
    await page.route('**/api/admin/office-locations', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 400, json: { error: 'INVALID_RADIUS', message: 'Bán kính không hợp lệ' } });
      } else {
        await route.fulfill({ status: 200, json: MOCK_LOCATIONS });
      }
    });
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: '+ Thêm vị trí' }).click();
    await page.locator('#name').fill('VP Test');
    await page.locator('#latitude').fill('10.77');
    await page.locator('#longitude').fill('106.70');
    await page.getByRole('button', { name: 'Thêm vị trí' }).last().click();

    await expect(page.getByText('Có lỗi xảy ra, vui lòng thử lại')).toBeVisible();
  });

  test('nút Hủy đóng modal thêm mới không lưu', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: '+ Thêm vị trí' }).click();
    await expect(page.getByRole('heading', { name: 'Thêm vị trí văn phòng' })).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByRole('heading', { name: 'Thêm vị trí văn phòng' })).not.toBeVisible();
  });

  // ── Edit modal ────────────────────────────────────────────────────────

  test('nút Sửa mở modal với dữ liệu vị trí hiện có', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: 'Sửa Văn phòng chính' }).click();

    await expect(page.getByRole('heading', { name: 'Sửa vị trí văn phòng' })).toBeVisible();
    await expect(page.locator('#name')).toHaveValue('Văn phòng chính');
    await expect(page.locator('#radiusMeters')).toHaveValue('200');
  });

  test('cập nhật vị trí thành công → toast success', async ({ page }) => {
    await page.route('**/api/admin/office-locations', async (route) => {
      await route.fulfill({ status: 200, json: MOCK_LOCATIONS });
    });
    await page.route('**/api/admin/office-locations/1', async (route) => {
      await route.fulfill({ status: 200, json: { ...MOCK_LOCATIONS[0], radiusMeters: 250 } });
    });
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: 'Sửa Văn phòng chính' }).click();
    await page.locator('#radiusMeters').fill('250');
    await page.getByRole('button', { name: 'Cập nhật' }).click();

    await expect(page.getByText('Cập nhật vị trí thành công')).toBeVisible();
  });

  // ── Toggle active ─────────────────────────────────────────────────────

  test('bấm nút bật/tắt trạng thái gọi API cập nhật', async ({ page }) => {
    let putCalled = false;
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.route('**/api/admin/office-locations/1', async (route) => {
      putCalled = true;
      await route.fulfill({ status: 200, json: { ...MOCK_LOCATIONS[0], active: false } });
    });
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: 'Tắt Văn phòng chính' }).click();

    await expect.poll(() => putCalled).toBe(true);
  });

  // ── Delete flow ────────────────────────────────────────────────────────

  test('nút xóa mở dialog xác nhận', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: 'Xóa Văn phòng chính' }).click();

    await expect(page.getByText('Xác nhận xóa vị trí')).toBeVisible();
    await expect(page.getByText(/Văn phòng chính/).last()).toBeVisible();
  });

  test('xóa vị trí thành công → toast success', async ({ page }) => {
    await page.route('**/api/admin/office-locations', async (route) => {
      await route.fulfill({ status: 200, json: MOCK_LOCATIONS });
    });
    await page.route('**/api/admin/office-locations/1', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 204, body: '' });
      }
    });
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: 'Xóa Văn phòng chính' }).click();
    await page.getByRole('button', { name: 'Xóa' }).last().click();

    await expect(page.getByText('Xóa vị trí thành công')).toBeVisible();
  });

  test('nút Hủy trong dialog xóa không xóa vị trí', async ({ page }) => {
    await page.route('**/api/admin/office-locations', route =>
      route.fulfill({ status: 200, json: MOCK_LOCATIONS })
    );
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: 'Xóa Văn phòng chính' }).click();
    await expect(page.getByText('Xác nhận xóa vị trí')).toBeVisible();

    await page.getByRole('button', { name: 'Hủy' }).click();
    await expect(page.getByText('Xác nhận xóa vị trí')).not.toBeVisible();
    await expect(page.getByText('Văn phòng chính')).toBeVisible();
  });

  test('xóa vị trí thất bại → toast lỗi', async ({ page }) => {
    await page.route('**/api/admin/office-locations', async (route) => {
      await route.fulfill({ status: 200, json: MOCK_LOCATIONS });
    });
    await page.route('**/api/admin/office-locations/1', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 500, json: { error: 'INTERNAL_ERROR' } });
      }
    });
    await page.goto('/admin/office-locations');

    await page.getByRole('button', { name: 'Xóa Văn phòng chính' }).click();
    await page.getByRole('button', { name: 'Xóa' }).last().click();

    await expect(page.getByText('Xóa vị trí thất bại, vui lòng thử lại')).toBeVisible();
  });
});
