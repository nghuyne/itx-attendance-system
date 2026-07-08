# Hướng dẫn BMAD Method (áp dụng chung cho mọi dự án)

> Tài liệu này mô tả **cách công ty sử dụng BMAD Method** từ đầu đến cuối: từ ý tưởng → PRD → thiết kế hệ thống → chia story → implement → test. Đây là tài liệu **chung**, không gắn với một dự án cụ thể — mục tiêu là nhân viên mới, dù vào bất kỳ dự án nào, đọc xong có thể tự áp dụng quy trình mà không cần được hướng dẫn lại.
>
> Với chi tiết riêng của từng dự án (tech stack, danh sách epic/story thật, ADR, cấu hình test...), xem file phụ lục riêng của dự án đó (ví dụ với ITX: `docs/bmad-phu-luc-itx.md`).

---

## Mục lục

1. [BMAD Method là gì?](#1-bmad-method-là-gì)
2. [Cấu trúc thư mục chuẩn của BMAD](#2-cấu-trúc-thư-mục-chuẩn-của-bmad)
3. [Cài đặt môi trường](#3-cài-đặt-môi-trường)
4. [Phase 1 — Product Discovery](#4-phase-1--product-discovery)
5. [Phase 2 — System Design](#5-phase-2--system-design)
6. [Phase 3 — Execution (Implementation)](#6-phase-3--execution-implementation)
7. [Testing trong quy trình BMAD](#7-testing-trong-quy-trình-bmad)
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

**Các agent trong BMAD (áp dụng cho mọi dự án):**

| Agent | Tên | Vai trò | Output chính |
|---|---|---|---|
| Analyst | Mary | Nghiên cứu domain, tạo product brief | `product-brief.md` |
| Product Manager | John | Viết PRD, tạo epic và story | `prd-*.md`, `epic-*.md` |
| Architect | Winston | Chọn tech stack, thiết kế DB/API | `architecture.md`, `ADR-*.md` |
| UX Designer | Sally | Thiết kế UI/UX | `DESIGN.md` |
| Dev | Amelia | Implement story theo TDD | Code + tests |
| Tech Writer | Paige | Viết documentation | Docs |

Tên agent và vai trò là quy ước cố định của BMAD, giống nhau ở mọi dự án — chỉ có nội dung output là khác nhau tuỳ dự án.

---

## 2. Cấu trúc thư mục chuẩn của BMAD

BMAD tạo ra cùng một bộ thư mục ở bất kỳ project nào được cài:

```
<project-root>/
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
│   │       └── prd-<tên-dự-án>-<ngày>/   # PRD + tất cả story files
│   │           ├── <epic>-<story>-<slug>.md
│   │           ├── deferred-work.md      # Bug/issue được hoãn lại từng story
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
│   │   └── 001-adr-*.md
│   └── ... (tài liệu riêng của từng dự án)
│
└── <mã nguồn dự án — khác nhau tuỳ tech stack>
```

**Quy tắc quan trọng về thư mục (áp dụng mọi dự án):**
- `_bmad-output/` → nơi BMAD xuất ra các artifact. **Không sửa tay** trừ khi cần điều chỉnh.
- `docs/` → BMAD đọc thư mục này làm "project knowledge". Thêm tài liệu tham khảo vào đây.
- Mỗi agent đọc artifact của agent trước để có context — đây là cách "handoff" hoạt động.
- Cấu trúc mã nguồn bên trong (`backend/`, `frontend/`, `src/`...) khác nhau tuỳ dự án — xem README hoặc phụ lục riêng của dự án đó.

---

## 3. Cài đặt môi trường

### 3.1 Yêu cầu chung

- Node.js v20.12+
- Python 3.10+ (hoặc `uv` package manager)
- Claude Code CLI (đã cài và đăng nhập)
- Runtime/công cụ riêng của dự án (Docker, JDK, v.v.) — xem README hoặc phụ lục riêng của dự án đó.

### 3.2 Cài BMAD Method vào một project

```bash
# Vào thư mục project
cd /path/to/project

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

BMAD sẽ quét project và hiển thị danh sách các bước tiếp theo. Đây là lệnh đầu tiên bạn nên gọi mỗi khi không biết làm gì tiếp — dùng được ở bất kỳ dự án nào đã cài BMAD.

### 3.4 Khởi động môi trường dev

Cách chạy dev server, database, service phụ trợ... khác nhau hoàn toàn tuỳ tech stack của từng dự án. Luôn tìm và đọc README/phụ lục riêng của dự án trước khi khởi động môi trường.

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
| `bmad-domain-research` | Nghiên cứu domain (ví dụ: quy trình nghiệp vụ thực tế của dự án) |
| `bmad-product-brief` | Tạo product brief từ kết quả brainstorm |

Mary sẽ hỏi những câu tương tự cho mọi dự án:
- Vấn đề đang giải quyết là gì?
- Đối tượng người dùng là ai?
- Tính năng cốt lõi cần có?
- Scope và constraint nào?

Output: `docs/product_brief.md`

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

Output: `_bmad-output/planning-artifacts/prds/prd-<tên-dự-án>-<ngày>.md`

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

Winston sẽ đọc PRD và hỏi về các quyết định kỹ thuật: tech stack, kiến trúc backend/frontend, database, deployment... Nội dung cụ thể phụ thuộc hoàn toàn vào dự án — Winston không áp một stack cố định.

**Output của bước này:**
- `docs/architecture/architecture.md` — full architecture document
- `docs/architecture/database_schema.md` — ERD và schema
- `docs/architecture/001-adr-*.md` — Architecture Decision Record(s)

**ADR (Architecture Decision Record)** là gì? Mỗi quyết định quan trọng được ghi lại theo format:
```markdown
## ADR-001: [Tên quyết định]

**Trạng thái:** Accepted

**Bối cảnh:** Tại sao cần quyết định này?

**Quyết định:** Chọn cái gì?

**Hệ quả:** Trade-off là gì?
```

### 5.2 Tạo Epic và Story (Scrum Master — Bob)

Sau khi có architecture, tạo danh sách epic và story:

```
bmad-create-epics-and-stories
```

BMAD sẽ chia PRD thành các epic (nhóm tính năng lớn) và trong mỗi epic là các story (task nhỏ có thể implement trong 1-2 ngày).

**Cấu trúc story file (mẫu chung):**

```markdown
## Story <epic>.<số>: <Tên story>

**Trạng thái:** Done | In Progress | Todo

### Acceptance Criteria
- AC-1: [Given] ... [When] ... [Then] ...
- AC-2: ...

### Dev Notes
- Ghi chú kỹ thuật cần lưu ý khi implement

### Tasks
- [ ] Task nhỏ 1
- [ ] Task nhỏ 2
```

Số lượng epic/story, tên gọi cụ thể là kết quả riêng của từng dự án — xem phụ lục riêng.

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
cat _bmad-output/planning-artifacts/prds/prd-*/sprint-status.yaml
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

Quy trình luôn giống nhau (đọc story → hỏi clarify nếu cần → viết test → implement → chạy test → báo cáo), bất kể ngôn ngữ/framework của dự án là gì.

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

Review này **adversarial** — agent cố tình tìm bug. Một số loại vấn đề agent thường phát hiện, bất kể stack nào:
- Missing null/undefined check
- Thiếu transaction/error boundary
- N+1 query hoặc vòng lặp không cần thiết
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

Output: file retrospective ghi lại what went well, what didn't, lessons learned, lưu tại `_bmad-output/planning-artifacts/prds/.../epic-<n>-retrospective.md`.

### 6.6 Xử lý thay đổi giữa sprint

Khi requirement thay đổi giữa chừng:

```
bmad-correct-course
```

Agent sẽ giúp update story/PRD mà không làm mất progress đã có.

---

## 7. Testing trong quy trình BMAD

BMAD không ép buộc một framework test cụ thể — nguyên tắc chung áp dụng cho mọi dự án:

- **Unit test**: mock dependency, test logic đơn lẻ. Dùng framework phù hợp với ngôn ngữ của dự án (JUnit, Jest, pytest, v.v.).
- **Integration test**: test với database/service thật (hoặc in-memory tương đương) thay vì mock toàn bộ, để tránh lệch giữa mock và hành vi thật ở production.
- **E2E test**: test trên UI/API thật từ góc nhìn người dùng (Playwright, Cypress, v.v. tuỳ dự án).

**Tạo test mới với BMAD:**
```
bmad-qa-generate-e2e-tests

# Agent sẽ hỏi:
# - Story hoặc tính năng nào cần test?
# - Các AC (Acceptance Criteria) cần cover?
# → Sinh ra spec file hoàn chỉnh
```

**Quy trình chuẩn trước khi merge (áp dụng chung, thay bằng lệnh thật của dự án):**
```bash
# 1. Chạy test backend/unit
<lệnh test của dự án>

# 2. Build check (nếu có compile/typecheck)
<lệnh build của dự án>

# 3. Chạy E2E nếu có
<lệnh e2e của dự án>

# 4. Xem report nếu có fail
```

Framework cụ thể, lệnh chạy test, cấu hình screenshot/video... xem phụ lục riêng của từng dự án.

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

### File quan trọng cần biết (đường dẫn chuẩn của BMAD)

| File | Mục đích |
|---|---|
| `_bmad/config.toml` | Cấu hình BMAD (project name, paths) |
| `docs/project_context.md` | "Hiến pháp" kỹ thuật — mọi agent đọc |
| `docs/architecture/architecture.md` | Full system architecture |
| `docs/architecture/database_schema.md` | ERD và table schema |
| `_bmad-output/planning-artifacts/prds/.../` | Tất cả story files |
| `_bmad-output/planning-artifacts/prds/.../sprint-status.yaml` | Trạng thái sprint |
| `_bmad-output/implementation-artifacts/deferred-work.md` | Technical debt backlog |

---

## Tài liệu tham khảo

- [BMAD Method GitHub](https://github.com/bmad-code-org/BMAD-METHOD)
- [BMAD Docs](https://docs.bmad-method.org)
- Phụ lục riêng của từng dự án (tech stack, epic/story thật, cấu hình test cụ thể) — ví dụ dự án ITX: `docs/bmad-phu-luc-itx.md`

---

*Tài liệu chuẩn áp dụng cho mọi dự án dùng BMAD Method. Cập nhật lần cuối: 2026-07-07.*
