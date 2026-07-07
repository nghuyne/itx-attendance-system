# Hướng dẫn BMAD Method — Dự án ITX Attendance

> Tài liệu này mô tả **cách dự án ITX sử dụng BMAD Method** từ đầu đến cuối: từ ý tưởng → PRD → thiết kế hệ thống → chia story → implement → test. Mục tiêu là người mới vào team có thể đọc tài liệu này và tự làm theo mà không cần được hướng dẫn thêm.

---

## Mục lục

1. [BMAD Method là gì?](#1-bmad-method-là-gì)
2. [Cấu trúc thư mục trong dự án ITX](#2-cấu-trúc-thư-mục-trong-dự-án-itx)
3. [Cài đặt môi trường](#3-cài-đặt-môi-trường)
4. [Phase 1 — Product Discovery](#4-phase-1--product-discovery)
5. [Phase 2 — System Design](#5-phase-2--system-design)
6. [Phase 3 — Execution (Implementation)](#6-phase-3--execution-implementation)
7. [Testing — Backend (JUnit) và Frontend (Playwright)](#7-testing--backend-junit-và-frontend-playwright)
8. [Quy tắc vàng khi làm việc với BMAD](#8-quy-tắc-vàng-khi-làm-việc-với-bmad)
9. [Cheat Sheet — Tóm tắt lệnh nhanh](#9-cheat-sheet--tóm-tắt-lệnh-nhanh)

---

## 1. BMAD Method là gì?

BMAD (Build More Architect Dreams) là một framework mã nguồn mở mô phỏng một team agile hoàn chỉnh bằng các AI agent chuyên biệt. Thay vì hỏi một AI "viết code cho tôi", BMAD ép bạn đi qua đúng quy trình: **phân tích → lên kế hoạch → thiết kế → implement từng story nhỏ**.

**Vấn đề BMAD giải quyết:**
- Một AI chat duy nhất cố làm PM + Architect + Developer cùng lúc → làm cái nào cũng kém.
- Context window đầy sau vài tiếng → chất lượng code giảm dần.
- Không có "bộ nhớ" giữa các session → mỗi lần bắt đầu lại từ đầu.

**Giải pháp:** mỗi agent chỉ làm một việc, handoff qua **FILE** trên disk (không phải qua bộ nhớ chat). PRD → Architecture → Story files là "contract" giữa các agent.

**Các agent trong dự án ITX:**

| Agent | Tên | Vai trò | Output chính |
|---|---|---|---|
| Analyst | Mary | Nghiên cứu domain, tạo product brief | `product-brief.md` |
| Product Manager | John | Viết PRD, tạo epic và story | `prd-*.md`, `epic-*.md` |
| Architect | Winston | Chọn tech stack, thiết kế DB/API | `architecture.md`, `ADR-*.md` |
| UX Designer | Sally | Thiết kế UI/UX | `DESIGN.md` |
| Dev | Amelia | Implement story theo TDD | Code + tests |
| Tech Writer | Paige | Viết documentation | Docs |

---

## 2. Cấu trúc thư mục trong dự án ITX

```
ITX/
├── _bmad/                          # Cấu hình BMAD (do installer sinh ra)
│   ├── config.toml                 # Cấu hình chính: project name, output paths
│   ├── custom/
│   │   ├── config.toml             # Override của team (commit vào git)
│   │   └── config.user.toml        # Override cá nhân (gitignore)
│   └── bmm/                        # Workflow definitions
│
├── _bmad-output/                   # TẤT CẢ artifact do BMAD tạo ra
│   ├── planning-artifacts/
│   │   └── prds/
│   │       └── prd-ITX-2026-06-02/ # PRD + tất cả story files của dự án
│   │           ├── 1-1-docker-compose-project-scaffold.md
│   │           ├── 1-2-user-entity-database-schema-foundation.md
│   │           ├── ... (40+ story files)
│   │           ├── deferred-work.md  # Bug/issue được hoãn lại từng story
│   │           └── sprint-status.yaml
│   └── implementation-artifacts/
│       └── tests/                  # Test summary sau mỗi epic
│
├── docs/                           # Knowledge base — BMAD đọc thư mục này
│   ├── product_brief.md
│   ├── project_context.md          # "Hiến pháp" kỹ thuật của dự án
│   ├── architecture/
│   │   ├── architecture.md
│   │   ├── database_schema.md
│   │   └── 001-adr-tech-stack-and-patterns.md
│   ├── workflow_Bmad.md            # Tài liệu nghiên cứu BMAD (tham khảo)
│   └── ... (các tài liệu khác)
│
├── backend/                        # Spring Boot
├── frontend/                       # React + Capacitor (Android)
│   ├── tests/e2e/                  # Playwright E2E tests
│   │   ├── auth/                   # Test auth flow
│   │   ├── admin/                  # Test admin pages
│   │   ├── employee/               # Test employee flow
│   │   ├── leader/                 # Test leader flow
│   │   └── notifications/
│   └── playwright.config.ts
└── scripts/                        # Các script tiện ích
```

**Quy tắc quan trọng về thư mục:**
- `_bmad-output/` → nơi BMAD xuất ra các artifact. **Không sửa tay** trừ khi cần điều chỉnh.
- `docs/` → BMAD đọc thư mục này làm "project knowledge". Thêm tài liệu tham khảo vào đây.
- Mỗi agent đọc artifact của agent trước để có context — đây là cách "handoff" hoạt động.

---

## 3. Cài đặt môi trường

### 3.1 Yêu cầu

- Node.js v20.12+
- Python 3.10+ (hoặc `uv` package manager)
- Claude Code CLI (đã cài và đăng nhập)
- Docker Desktop (để chạy MySQL + MinIO + backend)

### 3.2 Cài BMAD Method vào project mới

> **Dự án ITX đã cài sẵn.** Phần này dành cho khi bạn bắt đầu một dự án mới từ đầu.

```bash
# Vào thư mục project mới
cd /path/to/new-project

# Cài BMAD
npx bmad-method install

# Trả lời các câu hỏi của installer:
# - Module: chọn BMM (BMad Method - core)
# - IDE: chọn Claude Code
# - Project name: tên project của bạn
# - Language: Vietnamese (hoặc English)
# - Output folder: mặc định _bmad-output/
```

Sau khi cài xong, restart Claude Code để các skill được load.

### 3.3 Kiểm tra BMAD đã hoạt động

Mở Claude Code trong thư mục project, gõ:

```
bmad-help
```

BMAD sẽ quét project và hiển thị danh sách các bước tiếp theo. Đây là lệnh đầu tiên bạn nên gọi mỗi khi không biết làm gì tiếp.

### 3.4 Khởi động môi trường dev của ITX

```bash
# Từ thư mục gốc ITX/
docker-compose up -d        # Khởi động MySQL + MinIO + Backend + Nginx

# Hoặc chạy từng phần:
cd backend && ./mvnw spring-boot:run   # Backend (port 8080)
cd frontend && npm install && npm run dev  # Frontend (port 5173)
```

**Tài khoản test mặc định:** xem `docs/TEST_ACCOUNTS.md`

---

## 4. Phase 1 — Product Discovery

> **Mục tiêu:** Từ ý tưởng ban đầu → có `product-brief.md` và `prd.md` rõ ràng.

### 4.1 Bắt đầu từ ý tưởng (Analyst — Mary)

Mở Claude Code, mở chat **MỚI**, gọi agent Analyst:

```
bmad-agent-analyst
```

Agent Mary sẽ hỏi bạn về ý tưởng. Các lệnh bạn có thể dùng trong session này:

| Lệnh | Làm gì |
|---|---|
| `bmad-brainstorming` | Brainstorm tự do, khám phá problem space |
| `bmad-domain-research` | Nghiên cứu domain (ví dụ: quy trình chấm công thực tế) |
| `bmad-product-brief` | Tạo product brief từ kết quả brainstorm |

**Ví dụ thực tế của dự án ITX:**
```
# Prompt mẫu để tạo product brief
bmad-product-brief

# Mary sẽ hỏi:
# - Vấn đề đang giải quyết là gì?
# - Đối tượng người dùng là ai?
# - Tính năng cốt lõi cần có?
# - Scope và constraint nào?
```

Output: `docs/product_brief.md`

Trong ITX, product brief đã được tạo tại `docs/product_brief.md`. Đọc file này để hiểu scope ban đầu của hệ thống.

### 4.2 Tạo PRD (Product Manager — John)

PRD là tài liệu quan trọng nhất — nó là "single source of truth" cho toàn bộ dự án.

Mở chat **MỚI**:

```
bmad-agent-pm
```

Sau đó dùng lệnh:

```
bmad-create-prd
```

John sẽ đọc `product_brief.md` và hỏi thêm để tạo PRD. Quy trình:

1. John hỏi từng phần: goals, user stories, functional requirements, non-functional requirements, out-of-scope.
2. Mỗi requirement được viết theo định dạng **Given/When/Then** (BDD).
3. Khi John hỏi "Bạn có muốn review không?" → đọc kỹ, sửa những gì sai trước khi confirm.

Output: `_bmad-output/planning-artifacts/prds/prd-ITX-*.md`

**Lưu ý quan trọng:** Đọc kỹ PRD sau khi tạo. Agent không đọc được ý bạn — nếu requirement mơ hồ lúc này, bug sẽ xuất hiện ở Phase 3.

Để chỉnh sửa PRD sau này:
```
bmad-edit-prd
```

Để validate PRD có đủ chất lượng không:
```
bmad-validate-prd
```

### 4.3 Artifact sau Phase 1

Sau Phase 1, bạn phải có:
- `docs/product_brief.md` — mô tả ngắn gọn problem và solution
- `_bmad-output/planning-artifacts/prds/prd-[tên]-[ngày]/` — thư mục chứa PRD

---

## 5. Phase 2 — System Design

> **Mục tiêu:** Từ PRD → có architecture rõ ràng (tech stack, DB schema, API endpoints) và danh sách epic/story.

### 5.1 Thiết kế Architecture (Architect — Winston)

Mở chat **MỚI**:

```
bmad-agent-architect
```

Lệnh để tạo architecture:

```
bmad-create-architecture
```

Winston sẽ đọc PRD và hỏi về các quyết định kỹ thuật. Với dự án ITX, Winston đã đưa ra các quyết định sau (xem `docs/architecture/`):

**Tech stack của ITX:**
- Backend: Spring Boot 3.x + Java 21, Spring Security + JWT
- Frontend: React 18 + TypeScript + Vite, TanStack Query, Zustand
- Mobile: Capacitor (Android)
- Database: MySQL 8.0 (Flyway migrations)
- Storage: MinIO (ảnh check-in)
- Infra: Docker Compose + Nginx

**Output của bước này:**
- `docs/architecture/architecture.md` — full architecture document
- `docs/architecture/database_schema.md` — ERD và schema
- `docs/architecture/001-adr-tech-stack-and-patterns.md` — Architecture Decision Record

**ADR (Architecture Decision Record)** là gì? Mỗi quyết định quan trọng được ghi lại theo format:
```markdown
## ADR-001: [Tên quyết định]

**Trạng thái:** Accepted

**Bối cảnh:** Tại sao cần quyết định này?

**Quyết định:** Chọn cái gì?

**Hệ quả:** Trade-off là gì?
```

Với ITX có 3 ADR:
- `001-adr-tech-stack-and-patterns.md` — chọn Spring Boot + React
- `002-adr-public-ip-validation.md` — cách validate IP văn phòng
- `003-adr-hybrid-mac-ip-validation.md` — kết hợp MAC/BSSID cho Android

### 5.2 Tạo Epic và Story (Scrum Master — Bob)

Sau khi có architecture, tạo danh sách epic và story:

```
bmad-create-epics-and-stories
```

BMAD sẽ chia PRD thành các epic (nhóm tính năng lớn) và trong mỗi epic là các story (task nhỏ có thể implement trong 1-2 ngày).

**Cấu trúc story file (ví dụ `1-1-docker-compose-project-scaffold.md`):**

```markdown
## Story 1.1: Docker Compose Project Scaffold

**Trạng thái:** Done

### Acceptance Criteria
- AC-1: [Given] môi trường chưa cài gì [When] chạy docker-compose up [Then] tất cả service start
- AC-2: Backend health endpoint trả 200

### Dev Notes
- Dùng MySQL 8.0, không phải MariaDB
- MinIO bucket name: attendance-photos

### Tasks
- [ ] Tạo docker-compose.yml
- [ ] Viết Dockerfile cho backend
- [ ] Cấu hình Nginx reverse proxy
```

**Dự án ITX có 10 epic, 40+ story:**
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

### 5.3 Kiểm tra sẵn sàng triển khai

Trước khi bắt đầu code, chạy readiness check:

```
bmad-check-implementation-readiness
```

Gate này kiểm tra:
- PRD và Architecture có coherent không?
- Story có đủ acceptance criteria không?
- Dependency giữa story có rõ ràng không?

Kết quả: **PASS** / **CONCERNS** / **FAIL**. Chỉ bắt đầu code khi PASS hoặc CONCERNS đã được resolve.

### 5.4 Artifact sau Phase 2

Sau Phase 2, bạn phải có:
- `docs/architecture/architecture.md`
- `docs/architecture/database_schema.md`
- Các ADR file
- Thư mục story files trong `_bmad-output/planning-artifacts/prds/`
- `_bmad-output/planning-artifacts/prds/.../sprint-status.yaml`

---

## 6. Phase 3 — Execution (Implementation)

> **Mục tiêu:** Implement từng story một theo TDD, review code, track progress.

### 6.1 Sprint Planning

Mở chat **MỚI** cho mỗi sprint:

```
bmad-sprint-planning
```

Agent Bob sẽ giúp chọn story nào làm trong sprint này dựa trên priority và dependency. Output: cập nhật `sprint-status.yaml`.

**Xem trạng thái sprint hiện tại:**
```bash
cat _bmad-output/planning-artifacts/prds/prd-ITX-*/sprint-status.yaml
```

### 6.2 Implement Story (Dev — Amelia)

**Quy tắc quan trọng nhất:** mở chat **MỚI** cho mỗi story.

```
bmad-agent-dev
```

Sau đó:

```
bmad-dev-story
```

Amelia sẽ hỏi story nào bạn muốn làm, đọc story file đó, và implement theo quy trình **TDD**:

```
RED   → viết test fail trước
GREEN → viết code tối thiểu để test pass
REFACTOR → clean up code
```

**Ví dụ quy trình thực tế:**

```
# Bạn: muốn làm story 2.1 (Shift Management)
bmad-dev-story

# Amelia sẽ:
# 1. Đọc file 2-1-fixed-shift-management-admin.md
# 2. Hỏi clarify nếu cần
# 3. Viết test JUnit trước (backend) hoặc Playwright (E2E)
# 4. Implement API endpoint
# 5. Implement React component
# 6. Chạy test → confirm pass
# 7. Báo cáo kết quả
```

**Nếu cần quick dev (bug nhỏ, không cần full ceremony):**
```
bmad-quick-dev
# → Amelia hỏi task là gì → implement nhanh → self-check → done
```

### 6.3 Code Review

Sau khi implement xong, luôn chạy code review:

```
bmad-code-review
```

Review này **adversarial** — agent cố tình tìm bug. Một số thứ agent thường phát hiện:
- Missing null check
- Thiếu transaction boundary
- N+1 query
- Security issue (thiếu auth check, lộ sensitive data)

**Quan trọng:** review sẽ tìm tối thiểu 3 issue. Không phải tất cả đều critical — đánh giá từng cái và quyết định fix ngay hay defer vào `deferred-work.md`.

### 6.4 Quản lý Deferred Work

File `_bmad-output/implementation-artifacts/deferred-work.md` ghi lại tất cả issue được hoãn lại từ code review.

Format mỗi entry:
```markdown
## Deferred from: code review of [story-id] ([ngày])

- **[Tên issue]** — [Mô tả vấn đề]. [Lý do defer]. `[File:dòng]`
```

Trước khi ship production, đọc lại file này và ưu tiên fix những gì có risk cao.

### 6.5 Retrospective sau mỗi Epic

Sau khi xong một epic:

```
bmad-retrospective
```

Output: file retrospective ghi lại what went well, what didn't, lessons learned. Dự án ITX có:
- `_bmad-output/planning-artifacts/prds/.../epic-1-retrospective.md`
- `epic-6-retro-2026-06-22.md`
- `epic-7-retro-2026-06-22.md`
- `epic-8-retro-2026-06-22.md`

### 6.6 Xử lý thay đổi giữa sprint

Khi requirement thay đổi giữa chừng:

```
bmad-correct-course
```

Agent sẽ giúp update story/PRD mà không làm mất progress đã có.

---

## 7. Testing — Backend (JUnit) và Frontend (Playwright)

### 7.1 Backend Testing (JUnit + Spring Boot Test)

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

### 7.2 Frontend Testing — Playwright E2E

Playwright test chạy trên browser thật (Chromium), mock API responses, kiểm tra UI behavior.

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

### 7.3 Cấu hình Playwright — Ảnh và Video

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

**Thư mục `test-results/` có cấu trúc:**
```
test-results/
└── auth-login-Login-Page-redi-52592-oard-after-successful-login-chromium/
    ├── test-finished-1.png     # Screenshot cuối test
    └── video.webm              # Video toàn bộ test
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

### 7.4 Cách viết Playwright test mới

**Pattern chung trong ITX — mock API trước, test UI sau:**

```typescript
import { test, expect } from '@playwright/test';

// Mock data dùng chung
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
    // Mock login trước
    await page.route('**/api/auth/login', route =>
      route.fulfill({ status: 200, json: MOCK.admin })
    );
    await page.goto('/login');
    // Login...
  });

  test('creates a new shift successfully', async ({ page }) => {
    // Mock API endpoint cần test
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

    // Verify
    await expect(page.getByText('Ca sáng')).toBeVisible();
  });
});
```

**Tạo test mới với BMAD:**
```
bmad-qa-generate-e2e-tests

# Agent sẽ hỏi:
# - Story hoặc tính năng nào cần test?
# - Các AC (Acceptance Criteria) cần cover?
# → Sinh ra spec file hoàn chỉnh
```

### 7.5 Chạy test trước khi merge

Quy trình chuẩn trước khi merge branch:

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

**Lưu ý về 2 test backend pre-existing fail:**
- `HealthEndpointTest` → fail vì MinIO không chạy ở máy local, bỏ qua.
- Một Hibernate flakiness test ở `approveRequest/rejectRequest` → tái hiện được trên code gốc, là pre-existing infra issue, không phải bug code.

---

## 8. Quy tắc vàng khi làm việc với BMAD

### Luôn làm

**1. Mở chat MỚI cho mỗi workflow**
Quan trọng nhất. Context window đầy → chất lượng giảm. Artifact trên disk là cách carry state.

**2. Đọc kỹ artifact trước khi confirm**
Khi John (PM) hay Winston (Architect) hỏi "Bạn có muốn xem lại không?" — đọc kỹ. Sai ở đây → bug ở code.

**3. Ghi tất cả issue vào deferred-work.md**
Dù nhỏ. File này là memory của team về technical debt.

**4. Theo thứ tự phase**
Phase 1 → 2 → 3. Không nhảy thẳng vào code khi chưa có PRD và Architecture.

**5. Dùng bmad-help khi không biết làm gì**
```
bmad-help
```

### Không làm

**Không sửa tay story file khi đang implement**
Story file là contract. Nếu cần thay đổi → dùng `bmad-correct-course` để update đúng cách.

**Không dùng party mode tùy tiện**
Party mode (nhiều agent cùng chat) đốt context và token rất nhanh. Chỉ dùng khi cần thảo luận tradeoff quan trọng.

**Không implement feature không có trong PRD**
"Gold plating" làm scope creep. Thêm vào PRD trước (`bmad-edit-prd`), rồi mới làm.

**Không để deferred-work.md quá dài**
Review và triage ít nhất mỗi sprint. P0/P1 phải được fix trước khi release.

---

## 9. Cheat Sheet — Tóm tắt lệnh nhanh

### Lệnh BMAD theo phase

| Phase | Lệnh | Mô tả |
|---|---|---|
| Bất cứ lúc nào | `bmad-help` | Hướng dẫn bước tiếp theo |
| Phase 1 | `bmad-agent-analyst` | Load agent Analyst (Mary) |
| Phase 1 | `bmad-brainstorming` | Brainstorm ý tưởng |
| Phase 1 | `bmad-product-brief` | Tạo product brief |
| Phase 1 | `bmad-agent-pm` | Load agent PM (John) |
| Phase 1 | `bmad-create-prd` | Tạo PRD |
| Phase 1 | `bmad-edit-prd` | Sửa PRD đã có |
| Phase 1 | `bmad-validate-prd` | Validate chất lượng PRD |
| Phase 2 | `bmad-agent-architect` | Load agent Architect (Winston) |
| Phase 2 | `bmad-create-architecture` | Tạo architecture document |
| Phase 2 | `bmad-create-epics-and-stories` | Chia epic và story |
| Phase 2 | `bmad-check-implementation-readiness` | Quality gate trước khi code |
| Phase 3 | `bmad-sprint-planning` | Lên kế hoạch sprint |
| Phase 3 | `bmad-agent-dev` | Load agent Dev (Amelia) |
| Phase 3 | `bmad-dev-story` | Implement một story |
| Phase 3 | `bmad-quick-dev` | Quick dev cho task nhỏ |
| Phase 3 | `bmad-code-review` | Adversarial code review |
| Phase 3 | `bmad-correct-course` | Xử lý thay đổi giữa sprint |
| Phase 3 | `bmad-retrospective` | Retrospective sau epic |

### Lệnh test

```bash
# Backend — chạy tất cả
cd backend && ./mvnw test

# Backend — chạy một class
./mvnw test -Dtest=TênClassTest

# Frontend — build check
cd frontend && npm run build

# Playwright — chạy tất cả
cd frontend && npx playwright test

# Playwright — chạy một file
npx playwright test tests/e2e/auth/login.spec.ts

# Playwright — filter theo tên
npx playwright test --grep "tên test"

# Playwright — xem HTML report
npx playwright show-report

# Playwright — chỉ chạy test fail lần trước
npx playwright test --last-failed
```

### File quan trọng cần biết

| File | Mục đích |
|---|---|
| `_bmad/config.toml` | Cấu hình BMAD (project name, paths) |
| `docs/project_context.md` | "Hiến pháp" kỹ thuật — mọi agent đọc |
| `docs/architecture/architecture.md` | Full system architecture |
| `docs/architecture/database_schema.md` | ERD và table schema |
| `_bmad-output/planning-artifacts/prds/.../` | Tất cả story files |
| `_bmad-output/planning-artifacts/prds/.../sprint-status.yaml` | Trạng thái sprint |
| `_bmad-output/implementation-artifacts/deferred-work.md` | Technical debt backlog |
| `frontend/playwright.config.ts` | Cấu hình Playwright (screenshot, video) |
| `frontend/test-results/` | Ảnh + video của Playwright test |

---

## Tài liệu tham khảo

- [BMAD Method GitHub](https://github.com/bmad-code-org/BMAD-METHOD)
- [BMAD Docs](https://docs.bmad-method.org)
- [Playwright Docs](https://playwright.dev/docs/intro)
- `docs/workflow_Bmad.md` — Nghiên cứu chuyên sâu về BMAD so với các framework khác (Spec Kit, OpenSpec, GSD...)
- `docs/architecture/` — Architecture decisions của dự án ITX

---

*Tài liệu này được viết dựa trên thực tế triển khai dự án ITX Attendance System. Cập nhật lần cuối: 2026-07-07.*
