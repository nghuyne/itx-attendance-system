# System Architecture — ITX Smart Attendance System

**Version:** 1.0  
**Date:** 2026-06-04  
**Tech Stack:** Spring Boot 3.x + React 18 + MySQL 8.x + MinIO  
**Audience:** Backend/Frontend/DevOps Engineers

---

## 1. System Context (C4 Level 1)

```
┌───────────────────────────────────────────────────────────────────┐
│                                                                    │
│  ┌─────────────────────┐                                         │
│  │                     │                                         │
│  │   Employee/Leader   │  (Mobile/Desktop Browser)              │
│  │   Admin             │                                         │
│  │                     │                                         │
│  └──────────────┬──────┘                                         │
│                 │                                                │
│                 │ HTTPS / TLS 1.3                               │
│                 │ (Secure WebRTC for camera)                    │
│                 ↓                                                │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                                                            │ │
│  │        ITX Smart Attendance System (Web App)             │ │
│  │                                                            │ │
│  │  ┌─────────────────────────────────────────────────────┐ │ │
│  │  │                                                     │ │ │
│  │  │  React 18 SPA (Vite)                              │ │ │
│  │  │  - Employee: Check-in, History, Requests          │ │ │
│  │  │  - Leader: Roster, Approvals, Notifications       │ │ │
│  │  │  - Admin: Configuration, Audit Logs               │ │ │
│  │  │                                                     │ │ │
│  │  │  Polling: Notifications every 15 seconds           │ │ │
│  │  │  State: TanStack Query (server) + Zustand (auth/ui)│ │ │
│  │  │                                                     │ │ │
│  │  └──────────────────┬──────────────────────────────────┘ │ │
│  │                     │                                      │ │
│  │                     │ REST API (JSON)                      │ │
│  │                     │ JWT Bearer Token in header            │ │
│  │                     ↓                                       │ │
│  │  ┌─────────────────────────────────────────────────────┐ │ │
│  │  │                                                     │ │ │
│  │  │  Spring Boot 3.x REST API                          │ │ │
│  │  │  (Embedded Tomcat, port 8080)                      │ │ │
│  │  │                                                     │ │ │
│  │  │  - /api/auth/* (JWT, refresh)                      │ │ │
│  │  │  - /api/attendance/* (check-in/out, history)       │ │ │
│  │  │  - /api/requests/* (exception/adjustment approval) │ │ │
│  │  │  - /api/admin/* (shifts, MACs, holidays, override) │ │ │
│  │  │  - /api/leader/* (team roster, requests)           │ │ │
│  │  │  - /api/notifications/* (in-app notifications)     │ │ │
│  │  │                                                     │ │ │
│  │  │  Stateless, multi-threaded (Virtual Threads, J21)  │ │ │
│  │  │  No session storage — JWT is source of truth       │ │ │
│  │  │                                                     │ │ │
│  │  └──────────┬──────────────┬──────────────┬──────────┘ │ │
│  │             │              │              │             │ │
│  │ ┌───────────▼──┐ ┌────────▼──────┐ ┌────▼────────────┐ │ │
│  │ │  MySQL 8.x   │ │  MinIO (S3)   │ │ SMTP / Email    │ │ │
│  │ │  (Persistent)│ │  (Photo store)│ │ (Notification)  │ │ │
│  │ │              │ │               │ │                 │ │ │
│  │ │ - Attendance │ │ - check-in    │ │ - Leader alerts │ │ │
│  │ │ - Shifts     │ │   photos      │ │ - Request status│ │ │
│  │ │ - Requests   │ │ - check-out   │ │ - Incomplete    │ │ │
│  │ │ - Holidays   │ │   photos      │ │   records       │ │ │
│  │ │ - Audit logs │ │               │ │                 │ │ │
│  │ │ - Users      │ │ TTL: 5 years  │ │                 │ │ │
│  │ │              │ │ Presigned URL │ │                 │ │ │
│  │ │ Flyway       │ │ (1 hr TTL)    │ │ Internal SMTP   │ │ │
│  │ │ migrations   │ │               │ │ or external     │ │ │
│  │ └──────────────┘ └───────────────┘ └─────────────────┘ │ │
│  │                                                          │ │
│  │ Scheduled Jobs:                                         │ │
│  │ - IncompleteAttendanceJob (every 5 min, 07:00-22:00)  │ │
│  │ - AbsentRecordJob (daily, 00:05)                       │ │
│  │ - OT Recalc (after check-out, if needed)               │ │
│  │                                                          │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─ External Systems ────────────────────────────────────────────┐
│                                                                │
│ HR/Payroll System (Out of MVP Scope)                         │
│ - Bulk export of attendance records (.xlsx, .pdf)             │
│ - Planned for v1.1                                            │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## 2. Container Architecture (C4 Level 2)

### 2.1 Deployment Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                       Docker Compose Environment                  │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Nginx (Reverse Proxy + Frontend Serving)                    ││
│  │ - Port 80 (HTTP) / 443 (HTTPS)                              ││
│  │ - Serve React build (dist/)                                 ││
│  │ - Proxy /api → backend:8080                                 ││
│  │ - GZIP compression, caching headers                         ││
│  └─────────────────────────────────────────────────────────────┘│
│                         ↓                                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Spring Boot Application (backend)                           ││
│  │ - Container: openjdk:21-slim                               ││
│  │ - Port: 8080 (internal), 80 exposed via Nginx              ││
│  │ - JVM Heap: 512MB-1024MB (configurable)                    ││
│  │ - Virtual Threads enabled (JEP 444, Java 21)               ││
│  │ - Health check: /actuator/health                           ││
│  │                                                              ││
│  │ Spring Boot Embedded Services:                              ││
│  │ - @Scheduled jobs (no external scheduler)                   ││
│  │ - @Async thread pool (photo upload, notifications)          ││
│  │ - JavaMail (SMTP configuration)                             ││
│  └─────────────────────────────────────────────────────────────┘│
│         ↙                    ↙                     ↙             │
│                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐        │
│  │  MySQL 8.0   │   │   MinIO      │   │ Mail Server  │        │
│  │              │   │ (S3-compat.) │   │ (SMTP)       │        │
│  │ - Port 3306  │   │              │   │              │        │
│  │ - UTF8MB4    │   │ - Port 9000  │   │ - localhost  │        │
│  │ - Vol:db/    │   │   (API)      │   │ - or external│        │
│  │            │   │ - Port 9001  │   │   server     │        │
│  │ Flyway      │   │   (console)  │   │              │        │
│  │ auto-run    │   │              │   │ Internal or  │        │
│  │ on startup  │   │ - Vol:minio/ │   │ transactional│        │
│  │             │   │                  │   (SendGrid, │        │
│  │ Tables:    │   │ Presigned URL    │   AWS SES)    │        │
│  │ - attendance│   │ in backend       │              │        │
│  │ - shifts    │   │                  │              │        │
│  │ - requests  │   │ Internal bucket  │              │        │
│  │ - holidays  │   │ 'attendance'     │              │        │
│  │ - etc.      │   │                  │              │        │
│  └──────────────┘   └──────────────────┘   └──────────────┘    │
│                                                                  │
│ All services on same Docker network: 'itx-network'              │
│ Service discovery: Docker Compose DNS (hostname = service name) │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 Containers & Dependencies

| Container | Image | Port | Volume | Purpose |
|-----------|-------|------|--------|---------|
| **nginx** | nginx:latest | 80/443 | ./dist | Serve React + proxy /api |
| **backend** | openjdk:21 | 8080 | ./logs | Spring Boot app |
| **mysql** | mysql:8.0 | 3306 | ./db | Persistent data |
| **minio** | minio:latest | 9000/9001 | ./minio | S3-compatible storage |

---

## 3. Spring Boot Package Structure (Layered Architecture)

```
com.itx.attendance/
│
├── config/
│   ├── SecurityConfig.java          # Spring Security, JWT filter
│   ├── AsyncConfig.java             # @Async thread pool config
│   ├── StorageConfig.java           # S3/MinIO client setup
│   ├── MailConfig.java              # SMTP JavaMail config
│   ├── FlyWayConfig.java            # DB migration config (implicit)
│   └── SchedulerConfig.java         # @Scheduled jobs config
│
├── controller/
│   ├── AuthController.java          # POST /api/auth/login, /refresh, /logout
│   ├── AttendanceController.java    # POST /api/attendance/check-in, /check-out
│   │                                # GET /api/attendance/today, /history
│   ├── RequestController.java       # POST /api/requests/exception, /adjustment
│   │                                # PUT /api/requests/{id}/approve, /reject
│   ├── LeaderController.java        # GET /api/leader/team/attendance, /requests
│   ├── AdminController.java         # CRUD shifts, MACs, holidays, overrides
│   ├── NotificationController.java  # GET /api/notifications/pending
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice, error responses
│
├── service/
│   ├── AuthService.java             # JWT token generation, refresh logic
│   ├── AttendanceService.java       # State machine logic, status calculation
│   ├── AttendanceValidationService.java # MAC, GPS, photo validation (FR-1 to FR-5)
│   ├── RequestService.java          # Exception/Adjustment request handling
│   ├── ApprovalService.java         # Leader approval flow (FR-13)
│   ├── NotificationService.java     # In-app + email notification dispatch
│   ├── ShiftService.java            # Shift CRUD & config (FR-6)
│   ├── HolidayService.java          # Holiday management (FR-17, FR-18)
│   ├── OtCalculationService.java    # OT logic (FR-15, FR-16)
│   ├── AdminService.java            # Admin override, audit log (FR-19, FR-20)
│   └── PhotoService.java            # Photo compression, S3 upload (@Async)
│
├── repository/
│   ├── AttendanceRecordRepository.java    # Spring Data JPA
│   ├── ShiftRepository.java
│   ├── ExceptionRequestRepository.java
│   ├── AdjustmentRequestRepository.java
│   ├── OtRecordRepository.java
│   ├── ValidMacRepository.java
│   ├── HolidayRepository.java
│   ├── UserRepository.java
│   ├── NotificationRepository.java
│   ├── AuditLogRepository.java
│   └── [Custom queries via @Query for complex logic]
│
├── domain/
│   ├── AttendanceRecord.java        # @Entity, main entity
│   ├── Shift.java
│   ├── ExceptionRequest.java
│   ├── AdjustmentRequest.java
│   ├── OtRecord.java
│   ├── ValidMac.java
│   ├── Holiday.java
│   ├── User.java
│   ├── UserRole.java                # Enum: EMPLOYEE, LEADER, ADMIN
│   ├── AttendanceStatus.java        # Enum: ON_TIME, LATE_IN, EARLY_OUT, HALF_DAY, INCOMPLETE, ABSENT
│   ├── ApprovalSubStatus.java       # Enum: PENDING_APPROVAL, APPROVED, REJECTED, ADMIN_OVERRIDE
│   ├── RequestType.java             # Enum: LATE_IN, EARLY_OUT, HALF_DAY
│   ├── DayType.java                 # Enum: WEEKDAY, WEEKEND, HOLIDAY
│   ├── Notification.java
│   └── AuditLog.java
│
├── dto/
│   ├── request/
│   │   ├── CheckInRequest.java          # { macAddress, lat, lng, photoBase64, isClientSite }
│   │   ├── CheckOutRequest.java         # { lat?, lng?, photoBase64 }
│   │   ├── ExceptionRequestDto.java     # { attendanceRecordId, requestType, reason }
│   │   ├── AdjustmentRequestDto.java    # { attendanceRecordId, proposedCheckoutTime, reason }
│   │   ├── ApprovalDecisionDto.java     # { status, reason (if reject) }
│   │   ├── ShiftConfigDto.java
│   │   ├── MacManagerDto.java
│   │   └── LoginRequest.java            # { username, password }
│   │
│   └── response/
│       ├── AttendanceRecordDto.java     # Response DTO (no sensitive fields)
│       ├── ExceptionRequestDto.java
│       ├── NotificationDto.java
│       ├── AuthResponse.java            # { accessToken, refreshToken, user }
│       ├── ErrorResponse.java           # { timestamp, status, error, message, path }
│       ├── PageResponse.java            # { content, page, size, totalElements, totalPages }
│       └── [MapStruct mappers: XyzDtoMapper.java]
│
├── scheduler/
│   ├── IncompleteAttendanceJob.java  # Every 5 min: mark INCOMPLETE if no check-out after grace period (FR-9)
│   ├── AbsentRecordJob.java          # Daily at 00:05: auto-create ABSENT records (FR-10)
│   └── SchedulerConfig.java          # Cron expressions, timezone handling (UTC+7)
│
├── notification/
│   ├── NotificationEventPublisher.java   # Fire events when request created/approved
│   ├── InAppNotificationService.java     # Persist to DB, fetch in /notifications
│   ├── EmailNotificationService.java     # JavaMail SMTP sender
│   ├── NotificationTemplate.java        # Message templates (Vietnamese)
│   └── NotificationScheduler.java       # Batch send retries if failed
│
├── storage/
│   ├── PhotoStorageService.java      # Abstraction for S3/MinIO
│   ├── MinIOPhotoStorageImpl.java     # Implementation: put, get presigned URL, delete
│   └── PhotoCompressionUtil.java     # Image resize, quality reduction
│
├── security/
│   ├── JwtTokenProvider.java         # Token generation, validation, refresh
│   ├── JwtAuthenticationFilter.java  # Request filter: extract JWT, set SecurityContext
│   ├── JwtAuthenticationEntryPoint.java  # Handle 401 Unauthorized
│   ├── CustomUserDetailsService.java # Load user from DB for auth
│   ├── SecurityUtil.java             # Get current user from SecurityContext
│   └── PasswordEncoder.java          # BCrypt for password hashing
│
├── exception/
│   ├── GlobalExceptionHandler.java   # @RestControllerAdvice, standardize error responses
│   ├── AttendanceException.java      # Custom exceptions
│   ├── ValidationException.java
│   ├── UnauthorizedException.java
│   ├── ForbiddenException.java
│   └── [Other domain-specific exceptions]
│
├── util/
│   ├── TimeUtil.java                 # UTC ↔ UTC+7 conversion helpers
│   ├── DateUtil.java
│   ├── ValidationUtil.java           # MAC, GPS, email format checks
│   ├── HaversineUtil.java            # GPS distance calculation (FR-5)
│   ├── LoggingUtil.java              # Structured logging (SLF4J)
│   └── Constants.java                # Enums, magic strings (avoided in code)
│
├── aspect/
│   ├── LoggingAspect.java            # AOP: log method entry/exit, duration
│   ├── TransactionAspect.java        # Explicit @Transactional boundaries
│   └── AuthorizationAspect.java      # @PreAuthorize annotation processing
│
└── Application.java                  # @SpringBootApplication entry point
```

---

## 4. API Boundaries

### 4.1 Authentication API

| Method | Endpoint | Payload | Response | Auth Required | Role |
|--------|----------|---------|----------|---|---|
| **POST** | `/api/auth/login` | `{ username, password }` | `{ accessToken, refreshToken, user { id, name, role } }` | ❌ | Public |
| **POST** | `/api/auth/refresh` | `{}` | `{ accessToken, expiresIn }` | ✅ (refresh) | All |
| **POST** | `/api/auth/logout` | `{}` | `{ message }` | ✅ | All |

### 4.2 Attendance API (Employee)

| Method | Endpoint | Payload | Response | Role |
|--------|----------|---------|----------|---|
| **POST** | `/api/attendance/check-in` | `{ macAddress, lat?, lng?, photoBase64, isClientSite }` | `{ attendanceRecord }` | EMPLOYEE |
| **POST** | `/api/attendance/check-out` | `{ lat?, lng?, photoBase64 }` | `{ attendanceRecord (updated) }` | EMPLOYEE |
| **GET** | `/api/attendance/today` | — | `{ attendanceRecord? }` | EMPLOYEE |
| **GET** | `/api/attendance/history?from=&to=&page=&size=` | — | `{ content: [records], page, totalPages, ... }` | EMPLOYEE |

### 4.3 Request API (Exception/Adjustment)

| Method | Endpoint | Payload | Response | Role |
|--------|----------|---------|----------|---|
| **POST** | `/api/requests/exception` | `{ attendanceRecordId, requestType, reason }` | `{ exceptionRequest (created) }` | EMPLOYEE |
| **POST** | `/api/requests/adjustment` | `{ attendanceRecordId, proposedCheckoutTime, reason }` | `{ adjustmentRequest (created) }` | EMPLOYEE |
| **GET** | `/api/requests/pending` | — | `{ content: [requests], page, ... }` | LEADER, ADMIN |
| **PUT** | `/api/requests/{id}/approve` | `{}` | `{ request (approved) }` | LEADER, ADMIN |
| **PUT** | `/api/requests/{id}/reject` | `{ reason }` | `{ request (rejected) }` | LEADER, ADMIN |

### 4.4 Leader API

| Method | Endpoint | Payload | Response | Role |
|--------|----------|---------|----------|---|
| **GET** | `/api/leader/team/attendance?date=` | — | `{ content: [rosterItems], ... }` | LEADER |
| **GET** | `/api/leader/team/requests/pending` | — | `{ content: [requests], ... }` | LEADER |

### 4.5 Admin API

| Method | Endpoint | Payload | Response | Role |
|--------|----------|---------|----------|---|
| **GET** | `/api/admin/shifts` | — | `{ content: [shifts], ... }` | ADMIN |
| **POST** | `/api/admin/shifts` | `{ name, startTime, endTime, ... }` | `{ shift (created) }` | ADMIN |
| **PUT** | `/api/admin/shifts/{id}` | `{ fields_to_update, auditReason }` | `{ shift (updated) }` | ADMIN |
| **DELETE** | `/api/admin/shifts/{id}` | `{ auditReason }` | `{ message }` | ADMIN |
| **GET** | `/api/admin/valid-macs` | — | `{ content: [macs], ... }` | ADMIN |
| **POST** | `/api/admin/valid-macs` | `{ macAddress, scope, employeeId?, description, auditReason }` | `{ mac (created) }` | ADMIN |
| **DELETE** | `/api/admin/valid-macs/{id}` | `{ auditReason }` | `{ message }` | ADMIN |
| **GET** | `/api/admin/holidays` | — | `{ content: [holidays], ... }` | ADMIN |
| **POST** | `/api/admin/holidays` | `{ date, name, type, year, auditReason }` | `{ holiday (created) }` | ADMIN |
| **DELETE** | `/api/admin/holidays/{id}` | `{ auditReason }` | `{ message }` | ADMIN |
| **PUT** | `/api/admin/attendance/{id}/override` | `{ fields, auditReason }` | `{ attendanceRecord (updated) }` | ADMIN |
| **GET** | `/api/admin/audit-logs?from=&to=&adminId=&targetTable=` | — | `{ content: [logs], ... }` | ADMIN |

### 4.6 Notification API (Polling)

| Method | Endpoint | Response | Role |
|--------|----------|----------|---|
| **GET** | `/api/notifications/pending` | `{ content: [notifications], unreadCount }` | LEADER, ADMIN |
| **PUT** | `/api/notifications/{id}/read` | `{ notification (updated) }` | LEADER, ADMIN |
| **PUT** | `/api/notifications/read-all` | `{ updatedCount }` | LEADER, ADMIN |

---

## 5. Core System Logic

### 5.1 Attendance State Machine (Backend Implementation)

**Decision Point: Calculate status after check-in/out**

```java
// service/AttendanceService.java

public void checkOut(AttendanceRecord record) {
  // 1. Receive check-out time, GPS, photo
  record.setCheckOutTime(now);
  record.setCheckOutGps(gps);
  record.setCheckOutPhotoUrl(photoUrl);

  // 2. Calculate status based on State Machine (§5 from PRD)
  AttendanceStatus status = calculateStatus(record);
  record.setAttendanceStatus(status);
  record.setApprovalSubStatus(null); // Reset if no request

  // 3. Calculate OT if applicable (FR-15)
  if (status != INCOMPLETE && status != ABSENT) {
    OtRecord ot = calculateOt(record);
    if (ot != null) {
      otRecordRepository.save(ot);
    }
  }

  // 4. Persist
  attendanceRepository.save(record);

  // 5. Schedule notification if incomplete is detected later
  // (IncompleteAttendanceJob will handle this at grace_period timeout)
}

private AttendanceStatus calculateStatus(AttendanceRecord record) {
  Shift shift = record.getShift();
  LocalDateTime checkInTime = record.getCheckInTime();
  LocalDateTime checkOutTime = record.getCheckOutTime();
  LocalDateTime shiftStart = shift.getStartTime();
  LocalDateTime shiftEnd = shift.getEndTime();
  int lateThreshold = shift.getLateInThreshold();
  int earlyThreshold = shift.getEarlyOutThreshold();
  int halfDayThreshold = shift.getHalfDayThreshold();

  // Priority: HALF_DAY > LATE_IN_EARLY_OUT > LATE_IN/EARLY_OUT > ON_TIME
  
  boolean isLateIn = checkInTime.isAfter(shiftStart.plusMinutes(lateThreshold));
  boolean isEarlyOut = checkOutTime.isBefore(shiftEnd.minusMinutes(earlyThreshold));
  boolean isHalfDayLate = checkInTime.isAfter(shiftStart.plusMinutes(halfDayThreshold));
  boolean isHalfDayEarly = checkOutTime.isBefore(shiftEnd.minusMinutes(halfDayThreshold));

  if (isHalfDayLate || isHalfDayEarly) {
    return AttendanceStatus.HALF_DAY;
  }

  if (isLateIn && isEarlyOut) {
    return AttendanceStatus.LATE_IN_EARLY_OUT;
  }

  if (isLateIn) {
    return AttendanceStatus.LATE_IN;
  }

  if (isEarlyOut) {
    return AttendanceStatus.EARLY_OUT;
  }

  return AttendanceStatus.ON_TIME;
}
```

### 5.2 Async Photo Upload Pattern

**Requirement:** Photo upload must not block HTTP response thread (FR-3)

```java
// service/PhotoService.java

@Service
public class PhotoService {

  @Async("taskExecutor")
  public CompletableFuture<String> uploadPhotoAsync(byte[] photoBytes, String employeeId, String type) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        // 1. Compress image to ≤500KB
        byte[] compressed = compressImage(photoBytes);

        // 2. Upload to MinIO
        String key = generatePhotoKey(employeeId, type);
        storageService.putObject("attendance", key, compressed);

        // 3. Return S3 URL
        return storageService.getObjectUrl("attendance", key);
      } catch (Exception e) {
        logger.error("Photo upload failed for employee {}", employeeId, e);
        throw new StorageException("Failed to upload photo", e);
      }
    });
  }

  // Called from CheckInRequest handler:
  // 1. Receive check-in data (including photoBase64)
  // 2. Fire async upload: photoService.uploadPhotoAsync(...)
  // 3. Block on result: wait for URL to be available
  // 4. Create attendance record with photo URL
  // 5. Return response immediately (photo already in S3)
}

// config/AsyncConfig.java

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "taskExecutor")
  public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("photo-upload-");
    executor.initialize();
    return executor;
  }
}
```

### 5.3 Scheduled Jobs

**IncompleteAttendanceJob: Every 5 minutes during business hours**

```java
// scheduler/IncompleteAttendanceJob.java

@Component
public class IncompleteAttendanceJob {

  @Scheduled(
    cron = "0 */5 7-22 * * ?",  // Every 5 min, 07:00-22:00 UTC+7
    zone = "Asia/Ho_Chi_Minh"
  )
  public void markIncompleteRecords() {
    LocalDateTime gracePeriodEnd = LocalDateTime.now(UTC_PLUS_7)
      .minusMinutes(GRACE_PERIOD_MINUTES); // 30 min default

    List<AttendanceRecord> incomplete = attendanceRepository.findRecordsMissingCheckOut(
      gracePeriodEnd,
      AttendanceStatus.ON_TIME,
      AttendanceStatus.LATE_IN,
      AttendanceStatus.EARLY_OUT,
      AttendanceStatus.LATE_IN_EARLY_OUT
    );

    for (AttendanceRecord record : incomplete) {
      record.setAttendanceStatus(AttendanceStatus.INCOMPLETE);
      record.setApprovalSubStatus(ApprovalSubStatus.PENDING_ADJUSTMENT);
      attendanceRepository.save(record);

      // Fire notification: "Bản ghi chưa hoàn chỉnh"
      notificationService.notifyIncompleteRecord(record);
    }
  }
}
```

**AbsentRecordJob: Daily at 00:05 UTC+7**

```java
// scheduler/AbsentRecordJob.java

@Component
public class AbsentRecordJob {

  @Scheduled(
    cron = "0 5 0 * * ?",  // 00:05 every day, UTC+7
    zone = "Asia/Ho_Chi_Minh"
  )
  public void createAbsentRecords() {
    LocalDate yesterday = LocalDate.now(UTC_PLUS_7).minusDays(1);

    // Get all employees who should have worked yesterday
    List<User> employees = userRepository.findAll(); // or filter by active status

    // Get attendance records for yesterday
    List<AttendanceRecord> present = attendanceRepository.findByDate(yesterday);
    Set<String> presentEmployeeIds = present.stream()
      .map(AttendanceRecord::getEmployeeId)
      .collect(toSet());

    // Create ABSENT records for missing employees
    for (User employee : employees) {
      if (!presentEmployeeIds.contains(employee.getId())) {
        AttendanceRecord absent = new AttendanceRecord();
        absent.setEmployeeId(employee.getId());
        absent.setDate(yesterday);
        absent.setAttendanceStatus(AttendanceStatus.ABSENT);
        absent.setApprovalSubStatus(null);
        attendanceRepository.save(absent);
      }
    }
  }
}
```

### 5.4 Request Approval Flow

**Leader approval updates both request and attendance record atomically**

```java
// service/ApprovalService.java

@Transactional
public void approveRequest(String requestId, String approverId) {
  ExceptionRequest request = requestRepository.findById(requestId)
    .orElseThrow(() -> new EntityNotFoundException("Request not found"));

  AttendanceRecord record = request.getAttendanceRecord();

  // 1. Update request
  request.setStatus(RequestStatus.APPROVED);
  request.setReviewedBy(approverId);
  request.setUpdatedAt(now());
  requestRepository.save(request);

  // 2. Update record: sub-status = APPROVED
  // Main status (ON_TIME, LATE_IN, etc.) stays the same
  // But in reports, vi phạm sẽ không được tính
  record.setApprovalSubStatus(ApprovalSubStatus.APPROVED);
  record.setUpdatedAt(now());
  attendanceRepository.save(record);

  // 3. Fire notification to employee
  notificationService.notifyRequestApproved(request);
}

@Transactional
public void rejectRequest(String requestId, String approverId, String reason) {
  ExceptionRequest request = requestRepository.findById(requestId)
    .orElseThrow(() -> new EntityNotFoundException("Request not found"));

  // 1. Update request
  request.setStatus(RequestStatus.REJECTED);
  request.setReviewedBy(approverId);
  request.setReviewReason(reason);
  requestRepository.save(request);

  // 2. Record sub-status = REJECTED
  // Employee can resubmit
  request.getAttendanceRecord().setApprovalSubStatus(ApprovalSubStatus.REJECTED);
  attendanceRepository.save(request.getAttendanceRecord());

  // 3. Notify employee with rejection reason
  notificationService.notifyRequestRejected(request, reason);
}
```

---

## 6. Transaction & Concurrency Strategy

### 6.1 Database Transactions

**Optimistic Locking for Concurrent Updates:**

```java
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

  @Version
  private Long version;  // Hibernate auto-increments on update

  // If two processes try to update simultaneously:
  // - First update succeeds, version → 1
  // - Second update fails: OptimisticLockingFailureException
  // - Caller retries or handles error
}
```

### 6.2 Request Idempotency

**Prevent duplicate check-ins in the same minute:**

```java
// repository/AttendanceRecordRepository.java

@Query("""
  SELECT COUNT(r) FROM AttendanceRecord r
  WHERE r.employeeId = :employeeId
    AND DATE(r.checkInTime) = CURRENT_DATE
    AND MINUTE(TIMEDIFF(r.checkInTime, :nowTime)) = 0
""")
long countCheckInThisMinute(String employeeId, LocalDateTime nowTime);

// service/AttendanceService.java
if (countCheckInThisMinute(...) > 0) {
  throw new ConflictException("Already checked in this minute");
}
```

---

## 7. Security Considerations

### 7.1 JWT Authentication Flow

```
Client                          Backend
  │                               │
  ├─── POST /api/auth/login ────→ │
  │     { username, password }    │
  │                               │ [Validate credentials]
  │                               │ [Generate JWT]
  │ ← { accessToken, ...} ────────┤
  │
  ├─── GET /api/attendance/today ─┤
  │     Header: Authorization: Bearer {accessToken}
  │                               │ [Verify JWT signature]
  │                               │ [Extract claims: userId, role]
  │ ← { attendanceRecord }  ──────┤
  │
  │ [After 15 min, accessToken expires]
  │
  ├─── POST /api/auth/refresh ────┤
  │     [Refresh token in HttpOnly cookie]
  │                               │ [Verify refresh token]
  │                               │ [Generate new access token]
  │ ← { accessToken, expiresIn} ──┤
```

### 7.2 Photo URL Security

```
1. Admin/HR requests photo URL:
   GET /api/attendance/{id}/photo/url
   
2. Backend validates:
   - Requester is ADMIN or HR role
   - Attendance record exists
   - Photo was taken by employee (not spoofed)

3. Backend returns:
   Presigned S3 URL with TTL = 1 hour
   - This URL is time-limited
   - Can be shared, but expires quickly
   - Does NOT leak S3 secret key

4. Frontend opens URL in <img> or download link
   - URL is usable for 1 hour only
   - Prevents long-term exposure of S3 bucket structure
```

### 7.3 Audit Log Immutability

```java
@Entity
@Table(name = "audit_logs")
public class AuditLog {

  @Id
  @GeneratedValue
  private Long id;  // Auto-increment, never allow UPDATE/DELETE in repository

  // No @Version field
  // No setters for core fields

  @CreationTimestamp
  private LocalDateTime createdAt;
  // No updatedAt field — logs are immutable

  public AuditLog(String adminId, String targetTable, String targetId,
                  String fieldChanged, String oldValue, String newValue, String reason) {
    this.adminId = adminId;
    this.targetTable = targetTable;
    // ... immutable constructor
  }

  // No setters — final fields where possible
}

// In repository, NO update/delete methods:
// Only save(audit) and findAll/findBy...
```

---

## 8. Testing Strategy

### 8.1 Unit Tests (Service Layer)

```java
// test/java/com/itx/attendance/service/AttendanceServiceTest.java

@SpringBootTest
@DataJpaTest
class AttendanceServiceTest {

  @Autowired
  private AttendanceService service;

  @Test
  void testCalculateStatusOnTime() {
    Shift shift = shiftRepository.save(createShift(9, 0, 17, 30, 0, 0, 30, 30));
    AttendanceRecord record = createRecord(shift, 8, 45, 17, 30);

    AttendanceStatus status = service.calculateStatus(record);

    assertEquals(AttendanceStatus.ON_TIME, status);
  }

  @Test
  void testCalculateStatusLateIn() {
    Shift shift = shiftRepository.save(createShift(9, 0, 17, 30, 0, 0, 30, 30));
    AttendanceRecord record = createRecord(shift, 9, 20, 17, 30);

    AttendanceStatus status = service.calculateStatus(record);

    assertEquals(AttendanceStatus.LATE_IN, status);
  }

  @Test
  void testCalculateStatusHalfDay() {
    Shift shift = shiftRepository.save(createShift(9, 0, 17, 30, 0, 0, 30, 30));
    AttendanceRecord record = createRecord(shift, 9, 45, 17, 30); // > 30 min late

    AttendanceStatus status = service.calculateStatus(record);

    assertEquals(AttendanceStatus.HALF_DAY, status);
  }
}
```

### 8.2 Integration Tests (API Layer)

```java
// test/java/com/itx/attendance/controller/AttendanceControllerTest.java

@SpringBootTest
@AutoConfigureMockMvc
class AttendanceControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void testCheckInSuccess() throws Exception {
    String token = generateTestToken("employee1", UserRole.EMPLOYEE);

    mockMvc.perform(post("/api/attendance/check-in")
        .contentType(APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .content(objectMapper.writeValueAsString(checkInRequest)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.attendanceStatus").value("ON_TIME"));
  }

  @Test
  void testCheckInInvalidMac() throws Exception {
    String token = generateTestToken("employee1", UserRole.EMPLOYEE);
    CheckInRequest request = new CheckInRequest("INVALID:MAC:ADDRESS", ...);

    mockMvc.perform(post("/api/attendance/check-in")
        .contentType(APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").value("INVALID_MAC"));
  }
}
```

---

## 9. Deployment & DevOps

### 9.1 Docker Compose for Local/Dev

```yaml
# docker-compose.yml
version: '3.8'

services:
  nginx:
    image: nginx:latest
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./dist:/usr/share/nginx/html
    depends_on:
      - backend

  backend:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:mysql://mysql:3306/itx_attendance?useSSL=false&allowPublicKeyRetrieval=true
      DB_USERNAME: root
      DB_PASSWORD: password
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
      JWT_SECRET: ${JWT_SECRET:-dev-secret-key}
      MAIL_HOST: ${MAIL_HOST:-localhost}
    depends_on:
      - mysql
      - minio

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: itx_attendance
      MYSQL_CHARSET: utf8mb4
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"

  minio:
    image: minio/minio:latest
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data
    command: server /data --console-address :9001

volumes:
  mysql_data:
  minio_data:

networks:
  default:
    name: itx-network
```

### 9.2 Kubernetes Deployment (Future)

- Spring Boot: Stateless pod (horizontal scaling)
- MySQL: StatefulSet with persistent volume
- MinIO: StatefulSet or managed S3
- Nginx: Ingress controller
- Flyway: Init container (run migrations before app starts)

---

## 10. Performance & Scalability

### 10.1 Performance Targets (from PRD SM-5, SM-6)

| Metric | Target | How to Achieve |
|--------|--------|---|
| Check-in response | <2 seconds | Async photo upload, index on (employee_id, date) |
| Check-in completion (mobile 4G) | <30 seconds | Optimized UI, compress photo on client |
| API 95th percentile | <2 seconds | Connection pool, query caching, pagination |
| Photo upload | <10 seconds | Multi-threaded executor, async CompletableFuture |

### 10.2 Database Optimization

```sql
-- Key indexes (Flyway migration):
CREATE INDEX idx_attendance_employee_date 
  ON attendance_records(employee_id, date);
CREATE INDEX idx_attendance_status 
  ON attendance_records(attendance_status);
CREATE INDEX idx_requests_employee_status 
  ON exception_requests(employee_id, status);
CREATE INDEX idx_audit_logs_created_at 
  ON audit_logs(created_at DESC);
```

### 10.3 Scaling Strategy

**Horizontal Scaling:**
- Spring Boot stateless → Multiple instances behind Nginx load balancer
- MySQL: Vertical scaling first (single instance sufficient for <500 users)
- MinIO: S3 gateway or separate cluster
- Nginx: Load balance across multiple backend pods

**Caching (Future):**
- Redis for session data, notification counts (MVP uses database)
- Shift/Holiday data cached in application memory with TTL

---

## 11. Documentation & References

- **PRD:** `/docs/prd.md` (features FR-1 to FR-20, State Machine)
- **Addendum:** `/docs/addendum.md` (data model hints, API boundary)
- **Project Context:** `/docs/project_context.md` (tech stack, package structure, security rules)
- **UX Design:** `/docs/ux/` (component tree, state mapping, API contracts)
- **Database Schema:** `database_schema.md` (in this folder)
- **Architecture Decisions:** `001-adr-tech-stack-and-patterns.md` (in this folder)

---

**Last Updated:** 2026-06-04  
**Architecture Lead:** Claude Code (BMAD)  
**Status:** Draft v1.0 — Ready for Backend Implementation
