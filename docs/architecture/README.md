# Architecture Documentation — ITX Smart Attendance System

**Version:** 1.0  
**Created:** 2026-06-04  
**Status:** Ready for Backend Implementation  
**Audience:** Backend Engineers, Architects, DevOps

---

## 📚 Document Map

| Document | Purpose | Audience | Key Sections |
|----------|---------|----------|---|
| **architecture.md** | System overview & design | All | Context diagram, container flow, package structure, API boundaries, core logic |
| **database_schema.md** | MySQL schema & data model | Backend | All tables, columns, FKs, indexes, constraints, Flyway migrations |
| **001-adr-tech-stack-and-patterns.md** | Technical decisions | All | JWT auth, polling notifications, async uploads, state calculation, scheduled jobs |

---

## 🏗️ Quick Navigation

### For Backend Engineers (Java/Spring Boot)

1. **Start here:** `architecture.md` §3 (Spring Boot Package Structure)
   - Understand the layered architecture
   - See which services handle which features (FR-1 to FR-20)

2. **Data Model:** `database_schema.md`
   - Create MySQL 8.x tables using Flyway migrations
   - Understand foreign keys, indexes, constraints
   - Learn timezone handling (UTC storage, UTC+7 display)

3. **Core Logic:** `architecture.md` §5 (Core System Logic)
   - State machine implementation (ON_TIME, LATE_IN, HALF_DAY, etc.)
   - Async photo upload pattern
   - Scheduled jobs (INCOMPLETE, ABSENT detection)
   - Request approval flow

4. **API Design:** `architecture.md` §4 (API Boundaries)
   - All REST endpoints mapped to UX screens
   - Request/response payloads
   - Role-based access (EMPLOYEE, LEADER, ADMIN)

5. **Decisions:** `001-adr-tech-stack-and-patterns.md`
   - Why JWT + stateless backend
   - Why polling (not WebSocket) for notifications
   - Why async uploads with CompletableFuture
   - Why synchronous state calculation
   - Future upgrade paths (Redis, Quartz, etc.)

### For Frontend Engineers (React/TypeScript)

1. **API Contract:** `architecture.md` §4 (API Boundaries)
   - List of all endpoints
   - Request/response DTOs
   - Error response format
   - Pagination pattern

2. **Authentication Flow:** `001-adr-tech-stack-and-patterns.md` §1
   - JWT bearer token scheme
   - Access token (15 min) + Refresh token (7 days, HttpOnly)
   - How to implement in React

3. **Notification Polling:** `001-adr-tech-stack-and-patterns.md` §2
   - Poll `/api/notifications/pending` every 15 seconds
   - Use TanStack Query with `refetchInterval`
   - Display unread badge in header

4. **Data Entities:** `database_schema.md`
   - Field names, types, constraints
   - Enum values (AttendanceStatus, ApprovalSubStatus, etc.)
   - Foreign key relationships

### For DevOps / Deployment

1. **Container Setup:** `architecture.md` §2.2 (Deployment Architecture)
   - Docker Compose with Nginx, Spring Boot, MySQL, MinIO
   - Service dependencies
   - Volume mounts

2. **Database:** `database_schema.md`
   - MySQL 8.0 configuration (charset, collation, timezone)
   - Flyway migration strategy
   - Indexes for performance
   - Backup/recovery procedures

3. **Scheduled Jobs:** `architecture.md` §5.3 + `001-adr-tech-stack-and-patterns.md` §5
   - Spring @Scheduled cron expressions
   - Timezone handling
   - Idempotency requirements

### For Architects / Tech Leads

1. **System Context:** `architecture.md` §1–2
   - C4 diagrams (context, container levels)
   - Component interactions
   - Data flows (React ↔ API ↔ MySQL ↔ MinIO)

2. **Technology Decisions:** `001-adr-tech-stack-and-patterns.md` (all sections)
   - JWT vs sessions vs OAuth
   - Polling vs WebSocket vs SSE
   - Sync vs async state calculation
   - Single-instance vs distributed scheduling

3. **Scalability Considerations:** `architecture.md` §6–7
   - Transaction & concurrency strategy (optimistic locking)
   - Request idempotency
   - Security (JWT, photo URLs, audit logs)
   - Performance targets (SM-5, SM-6 from PRD)

---

## 🔑 Key Architectural Decisions

| Decision | Option | Reason | Future |
|----------|--------|--------|--------|
| **Auth** | JWT stateless | No session replication, horizontal scaling | None (works for any scale) |
| **Notifications** | HTTP polling 15s | Simple, testable, works on any network | v1.2: WebSocket option |
| **Photo Upload** | Async CompletableFuture | Non-blocking, fast response, reliable | Use Project Reactor in v2.0 |
| **State Calc** | Synchronous | Consistent, immediate, no eventual consistency issues | None (perfect fit) |
| **Jobs** | Spring @Scheduled | Zero DevOps, single instance | v1.1: Quartz for clustering |
| **Cache** | None (v1.0) | YAGNI, database is fast enough | v1.1: Redis for multi-instance |
| **Storage** | S3/MinIO presigned URLs | Secure, time-limited, no bucket exposure | Works for any scale |

---

## 📊 Package Structure Overview

```
Backend (Spring Boot):
com.itx.attendance/
├── config/              # Security, async, storage, scheduler
├── controller/          # REST API (thin layer)
├── service/             # Business logic
├── repository/          # Data access
├── domain/              # JPA entities
├── dto/                 # Request/response DTOs
├── scheduler/           # Scheduled jobs
├── notification/        # Email + in-app
├── storage/             # S3/MinIO abstraction
├── security/            # JWT, auth filters
├── exception/           # Error handling
└── util/                # Helpers, formatters

Frontend (React):
src/
├── components/          # UI components (employee, leader, admin)
├── hooks/               # Custom hooks (useAuth, useQuery, etc.)
├── services/            # API clients
├── store/               # Zustand state (auth, ui)
├── types/               # TypeScript interfaces
├── utils/               # Helpers (formatters, validators)
├── pages/               # Page-level components
└── App.tsx              # Router setup
```

---

## 🚀 Getting Started (Backend)

### 1. Set Up Database (Day 1)

```bash
# Create Flyway migrations in: src/main/resources/db/migration/
# V001__Initial_Schema.sql (all tables from database_schema.md)
# V002__Add_Indexes.sql (performance indexes)
# V003__Seed_Data.sql (default shifts, holidays)

# Run: Spring Boot auto-executes Flyway on startup
```

### 2. Implement Core Services (Day 2–3)

```java
// Priority order (based on UX critical path):
1. AuthService + JwtTokenProvider (lock user login flow)
2. AttendanceService + AttendanceValidationService (check-in)
3. PhotoService (async upload to MinIO)
4. RequestService + ApprovalService (exception handling)
5. SchedulerConfig + scheduled jobs (background tasks)
```

### 3. Implement Controllers (Day 4–5)

```java
// Order:
1. AuthController (/api/auth/*)
2. AttendanceController (/api/attendance/*)
3. RequestController (/api/requests/*)
4. LeaderController (/api/leader/*)
5. AdminController (/api/admin/*)
6. NotificationController (/api/notifications/*)
```

### 4. Testing Strategy

```java
// Unit tests: Service layer
// Integration tests: Controllers + DB
// E2E tests: Full flow (check-in → history → request → approval)
```

---

## 🔐 Security Checklist

- [ ] JWT with HTTPS only (no HTTP)
- [ ] BCrypt password hashing (cost ≥ 10)
- [ ] S3 presigned URLs with 1-hour TTL
- [ ] Audit logs immutable (no UPDATE/DELETE)
- [ ] CSRF protection (not needed for JSON API)
- [ ] SQL injection prevention (JPA queries, parameterized)
- [ ] XSS prevention (React auto-escapes, no dangerouslySetInnerHTML)
- [ ] Rate limiting (optional, v1.1)
- [ ] CORS configuration (nginx reverse proxy handles)

---

## 📈 Performance Targets (from PRD)

| Metric | Target | How to Achieve |
|--------|--------|---|
| Check-in completion (mobile 4G) | <30 seconds | Async upload, compression, polling |
| API response (95th percentile) | <2 seconds | Indexes on (employee_id, date), pagination |
| Photo upload | <10 seconds | Async CompletableFuture, compression |
| Notification latency | <30 seconds | Poll every 15 seconds, display update immediate |

---

## 🧪 Testing Checklist

### Unit Tests (Service)

```java
✓ AttendanceService.calculateStatus() for each state
✓ OtCalculationService for each day type
✓ HolidayService.determineDayType()
✓ PhotoService.compressImage() quality & size
✓ ValidationService.validateMac(), validateGps()
✓ RequestService.approveRequest(), rejectRequest()
```

### Integration Tests (API)

```java
✓ POST /api/auth/login (success, wrong password)
✓ POST /api/attendance/check-in (all statuses)
✓ POST /api/attendance/check-out (all statuses)
✓ POST /api/requests/exception (success, failure cases)
✓ PUT /api/requests/{id}/approve
✓ PUT /api/requests/{id}/reject (reason validation)
✓ GET /api/notifications/pending (polling)
✓ GET /api/leader/team/attendance (pagination)
✓ POST /api/admin/shifts (CRUD, audit reason)
```

### E2E Tests

```
✓ Employee check-in → check-out → ON_TIME
✓ Employee check-in late → check-out → LATE_IN → send request → leader approves
✓ Employee check-in → never check-out → INCOMPLETE (after grace period) → send adjustment
✓ Admin creates shift → employees assigned → check-in uses correct shift
✓ Holiday setup → OT calculated with 3.0x multiplier
```

---

## 🌐 API Design Patterns

### Request/Response Format

```json
POST /api/attendance/check-in
{
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "latitude": 21.0285,
  "longitude": 105.8542,
  "photoBase64": "iVBORw0KGgo...",
  "isClientSite": false
}

200 OK
{
  "id": "uuid",
  "employeeId": "emp123",
  "date": "2026-06-04",
  "checkInTime": "2026-06-04T08:45:00Z",
  "checkInMac": "AA:BB:CC:DD:EE:FF",
  "checkInLat": 21.0285,
  "checkInLng": 105.8542,
  "checkInPhotoUrl": "https://s3.../photo.jpg",
  "attendanceStatus": "ON_TIME",
  "approvalSubStatus": null,
  "createdAt": "2026-06-04T08:45:30Z"
}

400 Bad Request
{
  "timestamp": "2026-06-04T08:45:00Z",
  "status": 400,
  "error": "INVALID_MAC",
  "message": "MAC address not recognized. Enable Client Site Mode or contact Admin.",
  "path": "/api/attendance/check-in"
}
```

### Pagination

```json
GET /api/attendance/history?page=0&size=20

200 OK
{
  "content": [ { ... }, { ... }, ... ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "hasNext": true,
  "hasPrevious": false
}
```

---

## 📝 Code Style Guide

### Java Naming

- Classes: `PascalCase` — `AttendanceService`, `CheckInRequest`
- Methods: `camelCase` — `checkIn()`, `calculateStatus()`
- Constants: `UPPER_SNAKE_CASE` — `MAX_PHOTO_SIZE_KB`, `DEFAULT_OT_BUFFER`
- Packages: `lowercase` — `com.itx.attendance.service`
- Enums: `UPPER_SNAKE_CASE` values — `ON_TIME`, `LATE_IN`

### SQL Naming

- Tables: `snake_case`, plural — `attendance_records`, `exception_requests`
- Columns: `snake_case` — `check_in_time`, `is_client_site`
- Foreign keys: `{table_singular}_id` — `employee_id`, `shift_id`
- Indexes: `idx_{table}_{column}` — `idx_attendance_employee_date`

---

## 🔄 Development Workflow

### Week 1: Foundation

1. **DB Setup** — Implement all tables in Flyway migrations
2. **Auth** — JWT, Spring Security, login endpoint
3. **Photo Service** — MinIO integration, async upload

### Week 2: Core Features

4. **Attendance API** — Check-in, check-out, history
5. **Request Handling** — Exception, adjustment, approval
6. **Notifications** — In-app + email

### Week 3: Admin & Leader

7. **Admin APIs** — Shifts, MACs, holidays, overrides, audit logs
8. **Leader Dashboard** — Roster, pending requests
9. **Scheduled Jobs** — INCOMPLETE, ABSENT, OT calculation

### Week 4: Testing & Polish

10. **Unit & Integration Tests**
11. **Performance Tuning** — Indexes, query optimization
12. **Documentation** — API docs, deployment guide

---

## 🚦 Status & Next Steps

**Current Status:** ✅ Architecture Complete, Ready for Implementation

**Next Phase:** Backend Implementation (Spring Boot)
- [ ] Create Spring Boot project (mvn archetype)
- [ ] Configure database (Flyway, MySQL)
- [ ] Implement services layer
- [ ] Implement REST controllers
- [ ] Add integration tests
- [ ] Deploy to Docker Compose
- [ ] Frontend integration testing

**Future Enhancement:** ADR-002 (Caching Strategy with Redis — v1.1)

---

## 📞 Questions & References

- **PRD Details:** `/docs/prd.md` (business logic, state machine, features)
- **UX Mapping:** `/docs/ux/06-ui-state-mapping.md` (how UI reflects backend state)
- **Tech Stack:** `/docs/project_context.md` (guardrails, dependencies, config)
- **Product Brief:** `/docs/product_brief.md` (project vision, scope)

---

**Architecture Lead:** Claude Code (BMAD)  
**Last Updated:** 2026-06-04  
**Version:** 1.0 — Ready for Implementation Sprint
