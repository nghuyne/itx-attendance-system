# Phụ lục BMAD — Dự án ITX Attendance

> Tài liệu này bổ sung chi tiết **riêng của dự án ITX** cho quy trình chung ở [`docs/huong-dan-bmad-method.md`](./huong-dan-bmad-method.md). Đọc file đó trước để hiểu quy trình BMAD, rồi quay lại đây để biết ITX áp dụng cụ thể như thế nào.

---

## 1. Tech stack của ITX

- Backend: Spring Boot 3.x + Java 21, Spring Security + JWT
- Frontend: React 18 + TypeScript + Vite, TanStack Query, Zustand
- Mobile: Capacitor (Android)
- Database: MySQL 8.0 (Flyway migrations)
- Storage: MinIO (ảnh check-in)
- Infra: Docker Compose + Nginx

## 2. Khởi động môi trường dev

```bash
# Từ thư mục gốc ITX/
docker-compose up -d        # Khởi động MySQL + MinIO + Backend + Nginx

# Hoặc chạy từng phần:
cd backend && ./mvnw spring-boot:run   # Backend (port 8080)
cd frontend && npm install && npm run dev  # Frontend (port 5173)
```

**Tài khoản test mặc định:** xem `docs/TEST_ACCOUNTS.md`

## 3. Architecture Decision Records (ADR) thực tế

Xem `docs/architecture/`:
- `001-adr-tech-stack-and-patterns.md` — chọn Spring Boot + React
- `002-adr-public-ip-validation.md` — cách validate IP văn phòng
- `003-adr-hybrid-mac-ip-validation.md` — kết hợp MAC/BSSID cho Android

## 4. Danh sách Epic

ITX có 10 epic, 40+ story (xem đầy đủ tại `_bmad-output/planning-artifacts/prds/prd-ITX-2026-06-02/`):

- Epic 1: Infrastructure & Auth (Story 1.1 → 1.5)
- Epic 2: Shift & IP Management (Story 2.1 → 2.3)
- Epic 3: Attendance Core (Story 3.1 → 3.6)
- Epic 4: Request Flow (Story 4.1 → 4.4)
- Epic 5: Admin Override & Audit (Story 5.1 → 5.2)
- Epic 6: Leave Management (Story 6.1 → 6.2)
- Epic 7: GPS & Export (Story 7.1 → 7.2)
- Epic 8: OT Pre-approval & Notification (Story 8.1 → 8.2)
- Epic 9: Department & Filter (Story 9.1 → 9.2)
- Epic 10: Android Wi-Fi Validation (Story 10.1 → 10.4)

Retrospective sau mỗi epic: `_bmad-output/planning-artifacts/prds/.../epic-<n>-retrospective.md` (ví dụ `epic-6-retro-2026-06-22.md`).

## 5. Testing — Backend (JUnit + Spring Boot Test)

**Chạy tất cả test:**
```bash
cd backend
./mvnw test
```

**Chạy test cho một class cụ thể:**
```bash
./mvnw test -Dtest=AttendanceServiceTest
./mvnw test -Dtest=ShiftServiceTest,RequestServiceTest
```

**Cấu trúc test backend:**
```
backend/src/test/java/com/itx/attendance/
├── service/          # Unit test (mock dependencies)
│   ├── AttendanceServiceTest.java
│   ├── ShiftServiceTest.java
│   └── RequestServiceTest.java
└── controller/       # Integration test (real DB - H2)
    └── AttendanceControllerIntegrationTest.java
```

**Nguyên tắc test backend trong ITX:**
- Integration test dùng H2 in-memory (không cần MySQL chạy).
- Không mock repository trong integration test — dùng DB thật để tránh mock/prod divergence.
- Mỗi test method phải độc lập (dùng `@Transactional` hoặc `@BeforeEach` cleanup).

**Ví dụ test mẫu (integration test):**
```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AttendanceControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ShiftRepository shiftRepository;

    @Test
    void checkIn_whenWindowNotOpen_returns400() throws Exception {
        // Arrange: tạo shift với cửa sổ mở trong 60 phút, bắt đầu từ now+2h
        Shift shift = createShiftStartingIn2Hours();

        // Act + Assert
        mockMvc.perform(post("/api/attendance/check-in")
                .header("Authorization", "Bearer " + getEmployeeToken())
                .contentType(APPLICATION_JSON)
                .content(checkInRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("CHECKIN_NOT_OPEN_YET"));
    }
}
```

**Xem test summary sau mỗi epic:**
```bash
ls _bmad-output/implementation-artifacts/tests/
cat _bmad-output/implementation-artifacts/tests/test-summary.md
```

## 6. Testing — Frontend (Playwright E2E)

**Cài dependencies:**
```bash
cd frontend
npm install
npx playwright install chromium
```

**Chạy tất cả Playwright test:**
```bash
cd frontend
npx playwright test
```

**Chạy test cho một file/suite cụ thể:**
```bash
npx playwright test tests/e2e/auth/login.spec.ts
npx playwright test tests/e2e/admin/
npx playwright test --grep "redirects ADMIN"    # filter theo tên test
```

**Cấu trúc test Playwright:**
```
frontend/tests/e2e/
├── auth/
│   ├── login.spec.ts           # Login, redirect theo role
│   ├── routing.spec.ts         # Protected route, redirect
│   ├── token-refresh.spec.ts   # Auto refresh token
│   ├── change-password.spec.ts
│   ├── forgot-password.spec.ts
│   └── reset-password.spec.ts
├── admin/
│   ├── shifts.spec.ts
│   ├── users.spec.ts
│   ├── holidays.spec.ts
│   ├── valid-ips.spec.ts
│   ├── valid-macs.spec.ts
│   ├── departments.spec.ts
│   ├── office-locations.spec.ts
│   ├── attendance-override.spec.ts
│   ├── attendance-export.spec.ts
│   ├── attendance-status-filter.spec.ts
│   └── audit-logs.spec.ts
├── employee/
├── leader/
└── notifications/
```

### Cấu hình Playwright — Ảnh và Video

File cấu hình `frontend/playwright.config.ts`:

```typescript
export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:5173',
    screenshot: 'on',          // Chụp ảnh MỌI test (pass và fail)
    video: 'on',               // Quay video MỌI test
    trace: 'on-first-retry',   // Ghi trace khi retry
    launchOptions: {
      slowMo: 500,             // Chậm 500ms giữa mỗi action (dễ xem video)
    },
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'npm run dev',    // Tự động start dev server
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
});
```

**Ảnh và video được lưu ở đâu?**
```bash
# Sau khi chạy test:
frontend/test-results/          # Ảnh + video của từng test case
frontend/playwright-report/     # HTML report tổng hợp

# Xem HTML report:
npx playwright show-report
```

**Điều chỉnh cấu hình capture:**
```typescript
// Chỉ chụp ảnh khi fail (tiết kiệm disk):
screenshot: 'only-on-failure'

// Chỉ quay video khi fail:
video: 'retain-on-failure'

// Tắt slowMo (chạy nhanh hơn):
slowMo: 0
```

### Pattern viết Playwright test trong ITX — mock API trước, test UI sau

```typescript
import { test, expect } from '@playwright/test';

const MOCK = {
  admin: {
    accessToken: 'mock-token',
    user: {
      id: 'admin-id', username: 'admin',
      fullName: 'System Administrator',
      role: 'ADMIN', mustChangePassword: false
    },
  },
};

test.describe('Shifts Management', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/login', route =>
      route.fulfill({ status: 200, json: MOCK.admin })
    );
    await page.goto('/login');
  });

  test('creates a new shift successfully', async ({ page }) => {
    await page.route('**/api/admin/shifts', route => {
      if (route.request().method() === 'POST') {
        route.fulfill({ status: 201, json: { id: 1, name: 'Ca sáng' } });
      } else {
        route.fulfill({ status: 200, json: [] });
      }
    });

    await page.goto('/admin/shifts');
    await page.getByRole('button', { name: 'Thêm ca' }).click();
    await page.getByLabel('Tên ca').fill('Ca sáng');
    await page.getByRole('button', { name: 'Lưu' }).click();

    await expect(page.getByText('Ca sáng')).toBeVisible();
  });
});
```

## 7. Quy trình chạy test trước khi merge (ITX)

```bash
# 1. Backend tests
cd backend && ./mvnw test
# Expect: BUILD SUCCESS, không có FAIL

# 2. Frontend build check
cd frontend && npm run build
# Expect: không có TypeScript error

# 3. Playwright E2E
cd frontend && npx playwright test
# Expect: tất cả test PASS

# 4. Xem report nếu có fail
npx playwright show-report
```

**Lưu ý về 2 test backend pre-existing fail (đã biết, không phải bug mới):**
- `HealthEndpointTest` → fail vì MinIO không chạy ở máy local, bỏ qua.
- Một Hibernate flakiness test ở `approveRequest/rejectRequest` → tái hiện được trên code gốc, là pre-existing infra issue, không phải bug code.

## 8. File tham khảo khác của ITX

- `docs/project_context.md` — "hiến pháp" kỹ thuật của ITX
- `docs/architecture/architecture.md`, `database_schema.md`
- `docs/TEST_ACCOUNTS.md` — tài khoản test
- `docs/workflow_Bmad.md` — nghiên cứu BMAD so với các framework khác (Spec Kit, OpenSpec, GSD...)
- `_bmad-output/implementation-artifacts/deferred-work.md` — technical debt backlog hiện tại

---

*Phụ lục riêng cho dự án ITX Attendance System. Cập nhật lần cuối: 2026-07-07.*
