# Project Context — ITX Smart Attendance System

> **Mục đích:** Tài liệu này là "Technical Guardrails" dành cho AI Architect Agent. Mọi quyết định thiết kế kiến trúc phải tuân thủ các ràng buộc được định nghĩa ở đây. Đọc tài liệu này TRƯỚC khi đọc `prd.md`.

---

## 1. Tech Stack (Locked — Không được thay đổi)

### 1.1 Backend

| Layer | Technology | Version | Ghi chú |
|---|---|---|---|
| Language | Java | 21 | Sử dụng Virtual Threads nếu cần |
| Framework | Spring Boot | 3.x | |
| Security | Spring Security + JWT | — | Stateless, no session |
| ORM | Spring Data JPA + Hibernate | — | |
| Database | MySQL | 8.x | Xem §2 |
| Migration | Flyway | — | Bắt buộc, không dùng `ddl-auto=update` |
| Scheduler | Spring `@Scheduled` | — | Đủ cho FR-9, FR-10; không cần Quartz |
| Email | Spring Mail (JavaMail) | — | SMTP |
| Object Storage | AWS SDK v2 / MinIO Java SDK | — | S3-compatible |
| DTO Mapping | MapStruct | — | |
| Boilerplate | Lombok | — | |
| Validation | Jakarta Bean Validation | — | `@Valid` trên request body |
| Async | `@Async` + ThreadPoolTaskExecutor | — | Cho photo upload và notification |
| Testing | JUnit 5 + Mockito | — | |

**❌ Không dùng:** Kafka, RabbitMQ, WebSocket (xem §5 về notification), Redis (ngoài phạm vi MVP).

### 1.2 Frontend

| Layer | Technology | Version | Ghi chú |
|---|---|---|---|
| Language | TypeScript | 5.x | Bắt buộc |
| Framework | React | 18.x | |
| Build Tool | Vite | — | Không dùng CRA |
| HTTP Client | Axios | — | |
| Server State | TanStack Query (React Query) | v5 | Polling cho in-app notification |
| Routing | React Router | v6 | |
| Styling | Tailwind CSS | v3 | |
| Camera | react-webcam | — | Cho FR-3 chụp ảnh trực tiếp |
| Form | React Hook Form + Zod | — | |

**In-app notification:** Dùng **polling mỗi 15 giây** via TanStack Query `refetchInterval`. Không implement WebSocket cho MVP.

### 1.3 Infrastructure

| Component | Technology | Ghi chú |
|---|---|---|
| Containerization | Docker + Docker Compose | Toàn bộ stack chạy bằng một lệnh `docker compose up` |
| Object Storage | MinIO (self-hosted) | Chạy trong Docker Compose; S3-compatible, $0 cost |
| Reverse Proxy | Nginx | Serve React build + proxy API requests |
| Web Server | Embedded Tomcat (Spring Boot default) | Không cần cài riêng |

---

## 2. Database

- **Engine:** MySQL 8.x
- **Charset:** `utf8mb4` — bắt buộc (hỗ trợ tiếng Việt và emoji trong lý do/ghi chú)
- **Collation:** `utf8mb4_unicode_ci`
- **Timezone:** Lưu tất cả `TIMESTAMP` theo **UTC**. Tầng service chịu trách nhiệm convert UTC ↔ UTC+7 khi hiển thị.
- **Migration:** Flyway — mọi thay đổi schema đều qua migration script, đặt trong `src/main/resources/db/migration/`. Không bao giờ dùng `spring.jpa.hibernate.ddl-auto=update` ở môi trường nào ngoài local.
- **Data Model tham khảo:** Xem `addendum.md` §A1 — đây là gợi ý, Architect có thể điều chỉnh nhưng phải giữ nguyên tên các field key được đề cập trong PRD.

---

## 3. Kiến trúc Backend

### 3.1 Package Structure (Layered Architecture)

```
com.itx.attendance/
├── config/          # Spring config: Security, Async, S3, Mail, Flyway
├── controller/      # REST controllers (thin layer, chỉ validate + delegate)
├── service/         # Business logic (stateless)
├── repository/      # Spring Data JPA repositories
├── domain/          # JPA Entities
├── dto/             # Request/Response DTOs (MapStruct mappings)
├── scheduler/       # @Scheduled jobs (FR-9, FR-10)
├── notification/    # Notification service (in-app + email)
├── storage/         # S3/MinIO abstraction
├── security/        # JWT filter, UserDetails
└── exception/       # Global exception handler
```

### 3.2 Quy tắc Layer

- **Controller** không chứa business logic. Chỉ: validate input → gọi Service → trả response.
- **Service** không phụ thuộc lẫn nhau trực tiếp (tránh circular dependency). Nếu cần giao tiếp, dùng event hoặc inject qua interface.
- **Repository** chỉ chứa query. Không có logic tính toán.
- **Entity** không expose ra ngoài controller. Luôn convert sang DTO trước khi return.

### 3.3 Xử lý Lỗi (Global Exception Handler)

Tất cả exception phải được bắt tại `@RestControllerAdvice`. Response lỗi chuẩn hóa theo format:

```json
{
  "timestamp": "2026-06-02T10:00:00Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "MAC address không hợp lệ",
  "path": "/api/attendance/check-in"
}
```

Không để exception raw leak ra client. Không dùng `e.printStackTrace()` trong production code.

---

## 4. API Conventions

### 4.1 URL Pattern

```
/api/{resource}                    # Collection
/api/{resource}/{id}               # Single resource
/api/{resource}/{id}/{sub-resource}
```

Ví dụ: `/api/attendance/check-in`, `/api/requests/{id}/approve`

- Dùng **kebab-case** cho URL (`check-in`, không phải `checkIn`)
- Dùng **noun**, không dùng verb trong URL (trừ action endpoints như `/approve`, `/reject`)
- Version API: không cần versioning cho MVP (internal tool)

### 4.2 HTTP Methods

| Method | Dùng khi |
|---|---|
| `GET` | Đọc dữ liệu, không có side effect |
| `POST` | Tạo mới hoặc action (check-in, approve) |
| `PUT` | Update toàn bộ resource |
| `PATCH` | Update một phần |
| `DELETE` | Xóa |

### 4.3 Response Status

| Tình huống | Status |
|---|---|
| Thành công, có data | 200 OK |
| Tạo mới thành công | 201 Created |
| Thành công, không có data | 204 No Content |
| Lỗi client (validation, business rule) | 400 Bad Request |
| Chưa xác thực | 401 Unauthorized |
| Không có quyền | 403 Forbidden |
| Không tìm thấy | 404 Not Found |
| Lỗi server | 500 Internal Server Error |

### 4.4 Phân trang

Mọi endpoint trả list đều hỗ trợ phân trang:

```
GET /api/attendance/history?page=0&size=20&sort=date,desc
```

Response wrapper:

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}
```

---

## 5. Security

### 5.1 Authentication

- **JWT Bearer Token** trong `Authorization` header.
- Access token TTL: **15 phút**.
- Refresh token TTL: **7 ngày**, lưu trong HttpOnly cookie.
- Không lưu token ở localStorage (XSS risk).

### 5.2 Authorization (RBAC)

Ba role: `EMPLOYEE`, `LEADER`, `ADMIN`.

| Endpoint | EMPLOYEE | LEADER | ADMIN |
|---|---|---|---|
| Check-in/out | ✅ (chính mình) | ✅ | ✅ |
| Xem lịch sử cá nhân | ✅ | ✅ | ✅ |
| Xem lịch sử nhóm | ❌ | ✅ (nhóm mình) | ✅ |
| Approve/Reject request | ❌ | ✅ (nhóm mình) | ✅ |
| Admin config (ca, MAC, ngày lễ) | ❌ | ❌ | ✅ |
| Override bản ghi | ❌ | ❌ | ✅ |
| Xem audit log | ❌ | ❌ | ✅ |

### 5.3 Photo Security

- S3/MinIO bucket **không public**.
- Ảnh chỉ truy cập qua **Presigned URL** với TTL ≤ 1 giờ.
- Chỉ ADMIN và HR mới gọi được endpoint lấy presigned URL của người khác.

---

## 6. Async & Performance

### 6.1 Photo Upload

Photo upload lên S3/MinIO phải chạy **async** — không block HTTP response thread.

Flow:
1. Client gửi ảnh (base64 hoặc multipart) kèm check-in data.
2. Controller nhận request, validate.
3. Service gọi `@Async uploadPhoto()` — trả về `CompletableFuture<String>` (URL).
4. Sau khi upload xong, tạo Attendance Record với photo URL.
5. Response trả về client chỉ sau khi URL có sẵn (không fire-and-forget cho step này).

### 6.2 Notification

Notification (email + in-app) gửi **async** sau khi business action hoàn thành:

```java
// Sau khi save request, fire notification async
notificationService.sendAsync(event);
```

Dùng `ThreadPoolTaskExecutor` với core pool = 5, max = 20.

### 6.3 Scheduled Jobs

| Job | Trigger | FR |
|---|---|---|
| `IncompleteAttendanceJob` | Mỗi 5 phút, trong giờ làm (07:00–22:00 UTC+7) | FR-9 |
| `AbsentRecordJob` | Hàng ngày lúc 00:05 UTC+7 (17:05 UTC) | FR-10 |

Cả hai job phải **idempotent** — chạy lại không tạo duplicate record.

---

## 7. Cấu hình Môi trường

Tất cả config nhạy cảm phải qua **environment variable**, không hardcode trong `application.properties`:

```yaml
# application.yml (template)
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  mail:
    host: ${MAIL_HOST}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}

storage:
  endpoint: ${MINIO_ENDPOINT}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket: ${MINIO_BUCKET}

jwt:
  secret: ${JWT_SECRET}
  access-token-expiry: 900      # 15 phút (giây)
  refresh-token-expiry: 604800  # 7 ngày (giây)
```

Môi trường: `local`, `dev`, `prod`. Dùng Spring Profiles.

---

## 8. Docker Compose (Target Setup)

```yaml
services:
  backend:   # Spring Boot app, port 8080
  frontend:  # Nginx serving React build, port 80
  mysql:     # MySQL 8, port 3306
  minio:     # MinIO, port 9000 (API) + 9001 (Console)
```

Tất cả service trong cùng một Docker network. Frontend gọi Backend qua Nginx reverse proxy (`/api` → `backend:8080`).

---

## 9. Quy ước Đặt tên

### Java
- Class: `PascalCase` — `AttendanceRecord`, `ExceptionRequestService`
- Method/Field: `camelCase` — `checkIn()`, `attendanceStatus`
- Constant: `UPPER_SNAKE_CASE` — `MAX_PHOTO_SIZE_KB`
- Package: `lowercase` — `com.itx.attendance.service`
- Enum value: `UPPER_SNAKE_CASE` — `LATE_IN`, `PENDING_APPROVAL`

### Database
- Table: `snake_case`, số nhiều — `attendance_records`, `exception_requests`
- Column: `snake_case` — `check_in_time`, `is_client_site`
- FK: `{referenced_table_singular}_id` — `employee_id`, `shift_id`
- Index: `idx_{table}_{column}` — `idx_attendance_records_employee_date`

### API
- URL: `kebab-case` — `/api/valid-macs`, `/api/check-in`
- JSON body/response: `camelCase` — `checkInTime`, `attendanceStatus`

---

## 10. Ràng buộc Solo Developer

Đây là đồ án do **một developer** thực hiện với sự hỗ trợ của AI agents. Kiến trúc phải ưu tiên:

- **Đơn giản hơn tối ưu:** Tránh pattern phức tạp nếu có giải pháp đơn giản hơn đạt cùng kết quả.
- **Không over-engineer:** Không thêm abstraction layer không cần thiết (ví dụ: không cần CQRS, Event Sourcing).
- **Dễ debug:** Prefer synchronous flow có log rõ ràng hơn async flow phức tạp, trừ trường hợp bắt buộc (photo upload, notification).
- **Test ưu tiên:** Viết unit test cho service layer trước. Integration test là nice-to-have.

---

## 11. Tài liệu Liên quan

| File | Nội dung |
|---|---|
| `prd.md` | Business requirements, User Journeys, Feature list (FR-1 đến FR-20), State Machine |
| `addendum.md` | Gợi ý Data Model (field-level), phác thảo API boundary, Rejected Alternatives |
| `_decision-log.md` | Log các quyết định đã được xác nhận trong quá trình làm PRD |
| `product_brief.md` | Tóm tắt dự án gốc (input source) |
