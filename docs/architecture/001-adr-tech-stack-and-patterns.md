# ADR-001: Tech Stack & Architectural Patterns

**Status:** Accepted  
**Date:** 2026-06-04  
**Context:** MVP for ITX Smart Attendance System, solo developer with AI support  
**Audience:** Backend/Frontend engineers, architects, technical reviewers

---

## 1. Decision: Stateless Backend with JWT Authentication

### 1.1 Problem Statement

The system needs to authenticate users and maintain authorization context across requests without storing session state on the server. This is critical for:
- Horizontal scaling (multiple backend instances)
- Simplicity in DevOps (no sticky sessions, no session replication)
- Solo developer efficiency (no complex session management)

### 1.2 Decision

**Use JWT (JSON Web Tokens) with bearer token scheme.**

Access token (15 min TTL) + Refresh token (7 day TTL, HttpOnly cookie)

### 1.3 Rationale

| Alternative | Pros | Cons | Why Not |
|-----------|------|------|---------|
| **JWT Bearer Token** ✅ | Stateless, scalable, no session replication needed | Revocation is delayed (can't immediately block stolen token) | **CHOSEN** — Benefits outweigh the risk in MVP |
| Session Cookies (Tomcat) | Standard, simple | Requires session storage, sticky sessions, replication | Blocks horizontal scaling |
| OAuth 2.0 / OpenID Connect | Industry standard, third-party auth | Adds external dependency, more complexity | Overkill for internal tool |
| LDAP/Active Directory | Enterprise standard | Complexity, requires external server | Out of scope (MVP is internal only) |

### 1.4 Implementation Details

```
POST /api/auth/login
├─ Receive: { username, password }
├─ Validate: Check against users table (BCrypt password hash)
├─ Generate: 
│   ├─ accessToken (15 min): { userId, role, iat, exp }
│   └─ refreshToken (7 days): Stored in HttpOnly cookie (same-site: Strict)
└─ Return: { accessToken, expiresIn, user }

Subsequent requests:
├─ Header: Authorization: Bearer {accessToken}
└─ Filter: JwtAuthenticationFilter
   ├─ Extract & validate signature
   ├─ Check expiration
   ├─ Load user claims (userId, role)
   └─ Set SecurityContext (stateless)

Token refresh:
├─ POST /api/auth/refresh
├─ Validate refreshToken from HttpOnly cookie
├─ Generate new accessToken
└─ Return: { accessToken, expiresIn }
```

### 1.5 Security Measures

- **Password Hashing:** BCrypt with cost=10 (OWASP recommendation)
- **HTTPS Only:** All JWT transmission over TLS 1.3 (enforced in production)
- **HttpOnly Cookies:** Refresh token stored in HttpOnly, Secure, SameSite=Strict cookie (immune to XSS)
- **CSRF:** Not vulnerable (JSON API, not form-based)
- **Token Revocation:** No immediate revocation (trade-off for statelessness). TTLs mitigate risk:
  - Access token: 15 min (compromised token unusable after 15 min)
  - Refresh token: 7 days (can force logout by expiring cookie at next login)

### 1.6 Consequences

**Positive:**
- ✅ Stateless backend → Horizontal scaling effortless
- ✅ Simple DevOps → No session replication, no Redis needed
- ✅ Single-file authentication → Easier testing & debugging
- ✅ Mobile-friendly → Tokens work on any device

**Negative:**
- ❌ Token revocation is delayed (not immediate)
- ❌ Large token payload increases bandwidth (mitigated: keep claims minimal)
- ❌ Client-side token storage (XSS risk, mitigated by HttpOnly cookie for refresh)

### 1.7 Alternatives Considered & Rejected

**Why not WebSocket for real-time auth?**
- Over-engineering for MVP
- Polling is sufficient for 15-second notification interval
- Adds complexity in docker-compose setup

**Why not store JWT blacklist in Redis?**
- Defeats purpose of stateless (adds external dependency)
- Polling-based notifications don't require immediate revocation
- Can be added in v1.1 if needed

---

## 2. Decision: Polling-Based Notifications (not WebSocket)

### 2.1 Problem Statement

Leaders and Admins need real-time notifications when:
- Employees submit Exception/Adjustment Requests
- Scheduled jobs create INCOMPLETE/ABSENT records

Without real-time, leaders won't know to approve requests promptly.

### 2.2 Decision

**Use polling via TanStack Query, not WebSocket.**

Frontend polls `/api/notifications/pending` every 15 seconds.

### 2.3 Rationale

| Technology | Latency | Complexity | DevOps | Why Not |
|-----------|---------|-----------|--------|---------|
| **Polling (HTTP long-pull)** ✅ | 15 sec (acceptable for internal tool) | Low (simple REST endpoint) | Minimal | **CHOSEN** |
| WebSocket | <1 sec | Medium (requires: Spring WebSocket, browser support, test complexity) | Requires reverse proxy config (Nginx), persistent connections | Overkill for MVP; 15s latency acceptable |
| Server-Sent Events (SSE) | 1-5 sec | Medium | Similar to WebSocket | Slightly better than polling, but more complex than needed |
| Message Queue (RabbitMQ/Kafka) | Variable | High | Requires external service | Way over-engineering for MVP |

### 2.4 Implementation

**Backend (`/api/notifications/pending`):**

```java
@GetMapping("/api/notifications/pending")
public ResponseEntity<NotificationResponse> getPendingNotifications(
    @AuthenticationPrincipal UserDetails user) {
  
  List<Notification> pending = notificationRepository.findByRecipientAndIsReadFalse(user.getId());
  int unreadCount = pending.size();
  
  return ResponseEntity.ok(new NotificationResponse(
    pending,
    unreadCount
  ));
}
```

**Frontend (React Hook):**

```typescript
// hooks/useNotifications.ts
export const useNotifications = () => {
  return useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get('/api/notifications/pending'),
    refetchInterval: 15 * 1000,  // Poll every 15 seconds
    refetchOnWindowFocus: true,   // Also refetch when tab regains focus
    staleTime: 10 * 1000,        // Cache for 10 seconds to avoid duplicate fetches
  });
};
```

**React Component (Leader Dashboard):**

```typescript
function LeaderHeader() {
  const { data } = useNotifications();
  const unreadCount = data?.unreadCount || 0;

  return (
    <header>
      <button className="bell-icon">
        🔔 {unreadCount > 0 && <span className="badge">{unreadCount}</span>}
      </button>
    </header>
  );
}
```

### 2.5 Latency Analysis

For an employee submitting an Exception Request:

```
Timeline:
- 00:00 Employee submits request → API returns 201 Created
- 00:01 Notification created in DB
- 00:01–00:15 Leader polls every 15 sec (worst case: 15 sec delay)
- 00:15 Leader's UI updates with notification badge

Total latency: <30 seconds ✅ (acceptable for internal tool)
```

Compare to WhatsApp/Email (minutes to hours) — 15 seconds is excellent for HR workflow.

### 2.6 Scaling Implications

**Polling load (rough estimate):**
- 50 Leaders/Admins polling every 15 seconds
- Each request: ~1KB response
- Server: ~50 requests/15sec = 3.3 req/sec (negligible)

**WebSocket alternative:**
- 50 persistent connections
- More memory on server (connection state)
- More DevOps complexity (Nginx/HAProxy sticky sessions)

For MVP scale (<500 employees), polling is **vastly simpler** and **cheaper**.

### 2.7 Upgrade Path to WebSocket (v1.2)

If real-time becomes critical:

```java
// Spring WebSocket config (future, not in MVP)
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  // ...
}
```

The REST endpoint can stay as fallback. No breaking changes.

### 2.8 Consequences

**Positive:**
- ✅ Zero DevOps complexity (no WebSocket reverse proxy config)
- ✅ Simple caching (REST + TanStack Query = built-in stale-while-revalidate)
- ✅ Testable (just HTTP mocks)
- ✅ Works on any network (tunnels, corporate proxies, etc.)
- ✅ Graceful degradation (if network is slow, just wait 30 seconds instead of 15)

**Negative:**
- ❌ 15 second latency (acceptable for HR tool; not for trading platforms)
- ❌ Higher bandwidth than WebSocket (negligible at this scale)
- ❌ Cannot push to client (only client can pull)

---

## 3. Decision: Async Photo Upload with CompletableFuture

### 3.1 Problem Statement

Photo upload to MinIO must not block HTTP response thread. If upload is slow:
- Check-in endpoint hangs, violating <30 second SLA (SM-5)
- Frontend spinner hangs, poor UX
- Thread pool exhaustion on high load

### 3.2 Decision

**Use `@Async` with `CompletableFuture<String>` for photo upload.**

Check-in handler waits for upload to complete (blocking) before returning 201, but upload itself runs on a separate thread pool.

### 3.3 Rationale

| Approach | Thread Blocking | Queue Memory | Complexity | Why Not |
|----------|---|---|---|---|
| **Async + await** ✅ | No | Per-request | Low | **CHOSEN** — Best of both worlds |
| Fire-and-forget | No | No | Low | Risk: photo URL missing if upload fails; hard to retry |
| Sync (blocked) | Yes | No | Very Low | Violates <30 sec SLA; dangerous at scale |
| Reactive (Project Reactor) | No (non-blocking) | Yes | High | Overkill; Spring Boot @Async is sufficient |

### 3.4 Implementation

```java
// config/AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig {
  @Bean(name = "taskExecutor")
  public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);      // 5 active threads
    executor.setMaxPoolSize(20);      // Peak: 20 threads
    executor.setQueueCapacity(100);   // Queue up to 100 tasks
    executor.setThreadNamePrefix("photo-upload-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);  // Wait on shutdown
    executor.initialize();
    return executor;
  }
}

// service/PhotoService.java
@Service
public class PhotoService {

  @Async("taskExecutor")
  public CompletableFuture<String> uploadPhotoAsync(
      byte[] photoBytes,
      String employeeId,
      String type) {  // "check-in" or "check-out"
    
    return CompletableFuture.supplyAsync(() -> {
      try {
        // 1. Compress image
        byte[] compressed = compressImage(photoBytes);
        if (compressed.length > 500 * 1024) {
          throw new StorageException("Photo exceeds 500KB after compression");
        }

        // 2. Generate key: attendance/{year}/{month}/{employeeId}/{type}-{timestamp}.jpg
        String key = String.format(
          "attendance/%d/%02d/%s/%s-%d.jpg",
          LocalDate.now().getYear(),
          LocalDate.now().getMonthValue(),
          employeeId,
          type,
          System.currentTimeMillis()
        );

        // 3. Upload to MinIO
        storageService.putObject("attendance", key, compressed);

        // 4. Return S3 URL (or presigned URL for retrieval)
        return storageService.getObjectUrl("attendance", key);
      } catch (Exception e) {
        logger.error("Photo upload failed for employee {}", employeeId, e);
        throw new CompletionException(new StorageException("Upload failed", e));
      }
    });
  }

  // Helper: Image compression (CPU-bound, runs async)
  private byte[] compressImage(byte[] original) {
    // [BufferedImage, quality reduction logic]
    // Returns JPEG ≤ 500KB
  }
}

// controller/AttendanceController.java
@PostMapping("/api/attendance/check-in")
public ResponseEntity<AttendanceRecordDto> checkIn(
    @RequestBody CheckInRequest request,
    @AuthenticationPrincipal UserDetails user) {
  
  // 1. Validate MAC, GPS (quick checks)
  validateCheckInRequest(request);

  // 2. Upload photo ASYNC, wait for result
  String photoUrl;
  try {
    photoUrl = photoService.uploadPhotoAsync(
      Base64.getDecoder().decode(request.getPhotoBase64()),
      user.getId(),
      "check-in"
    ).get(10, TimeUnit.SECONDS);  // Wait max 10 seconds
  } catch (TimeoutException e) {
    return ResponseEntity.status(408)  // 408 Request Timeout
      .body(new ErrorResponse("Photo upload timeout"));
  } catch (ExecutionException e) {
    return ResponseEntity.status(500)
      .body(new ErrorResponse("Photo upload failed"));
  }

  // 3. Create attendance record with photo URL
  AttendanceRecord record = attendanceService.checkIn(
    request.getMacAddress(),
    request.getLatitude(),
    request.getLongitude(),
    photoUrl,
    request.isClientSite(),
    user.getId()
  );

  // 4. Return response (photo already in MinIO, URL in record)
  return ResponseEntity.status(201).body(mapper.toDto(record));
}
```

### 3.5 Execution Flow

```
Client Request (POST /api/attendance/check-in)
     ↓
Controller receives request
     ↓
Validate MAC/GPS/request format
     ↓
Fire @Async uploadPhotoAsync()
  ├─ Task added to thread pool queue
  ├─ Runs on separate thread (photo-upload-1)
  └─ Returns CompletableFuture<String>
     ↓
Controller calls .get(10, TimeUnit.SECONDS)
  ├─ **Blocks** current thread, waiting for photo URL
  ├─ Timeout after 10 seconds (fail-fast)
  └─ Returns URL: "s3://attendance/2026/06/emp123/check-in-1717532400.jpg"
     ↓
Create AttendanceRecord with URL
     ↓
Save to database
     ↓
Return 201 Created with record

Total time: ~5–10 seconds on fast network, never >30 seconds
```

### 3.6 Why Not Fire-and-Forget?

```java
// ❌ WRONG: Fire-and-forget
@PostMapping("/api/attendance/check-in")
public ResponseEntity<AttendanceRecordDto> checkIn(...) {
  photoService.uploadPhotoAsync(...)  // Don't wait
    .whenComplete((url, ex) -> {
      if (ex != null) logger.error("Upload failed", ex);
    });

  // Immediately return record without photo URL!
  AttendanceRecord record = attendanceService.checkIn(
    ... null /* no photo URL yet! */ ...
  );
  return ResponseEntity.status(201).body(mapper.toDto(record));
}
```

**Problem:** Record created without photo URL. If upload fails later:
- Frontend has incomplete data
- Admin audit doesn't show photo
- Retry logic is complex

✅ **Solution:** Block handler thread on photo upload, so we know it succeeded before returning.

### 3.7 Timeout & Error Handling

```java
try {
  photoUrl = photoService.uploadPhotoAsync(...)
    .get(10, TimeUnit.SECONDS);  // Fail fast
} catch (TimeoutException e) {
  // S3 is slow, but don't hang forever
  logger.warn("Photo upload timeout for employee {}", userId);
  return ResponseEntity.status(408).body(...);
} catch (ExecutionException e) {
  // S3 returned error (bucket full, network error, etc.)
  Throwable cause = e.getCause();
  logger.error("Photo upload failed: {}", cause.getMessage());
  return ResponseEntity.status(500).body(...);
} catch (InterruptedException e) {
  // Thread was interrupted (graceful shutdown?)
  Thread.currentThread().interrupt();
  return ResponseEntity.status(503).body(...);
}
```

### 3.8 Thread Pool Sizing

**Rationale:**
- Core pool = 5 threads (handles normal 5 concurrent uploads)
- Max pool = 20 threads (burst up to 20 concurrent uploads)
- Queue capacity = 100 (buffer 100 pending uploads if all threads busy)

**Load calculation:**
- Expected: 300 employees, 2 check-ins per day = 600 photo uploads/day
- Peak: 500 employees in office 9:00-9:30 AM = ~16 uploads/min = 0.27 uploads/sec
- Burst: Even if all 500 check in simultaneously: ~500 uploads in 30 seconds = 16 uploads/sec
- Thread pool can handle 5 concurrent + queue 100 = comfortable headroom

---

## 4. Decision: Synchronous State Calculation, Not Event-Driven

### 4.1 Problem Statement

Attendance state (ON_TIME, LATE_IN, etc.) is calculated after check-out and depends on:
- Shift configuration (thresholds)
- Check-in/out times
- Current date (for holiday classification)

Should this be:
- **Synchronous:** Calculate immediately in check-out handler
- **Asynchronous:** Fire event, process in background

### 4.2 Decision

**Use synchronous calculation in service layer.**

State is calculated and persisted in the check-out handler before returning response.

### 4.3 Rationale

| Approach | Consistency | Simplicity | Risk | Why Not |
|----------|-----------|-----------|------|---------|
| **Sync calculation** ✅ | Immediate | Low (direct computation) | None | **CHOSEN** |
| Event-driven (async) | Delayed (milliseconds to seconds) | High (events, handlers, orchestration) | State inconsistency if handler fails | Overkill; sync is fast enough |

### 4.4 Implementation

```java
// service/AttendanceService.java (check-out)

@Transactional
public AttendanceRecord checkOut(
    String attendanceRecordId,
    LocalDateTime checkOutTime,
    Double lat,
    Double lng,
    String photoBase64) {
  
  AttendanceRecord record = repo.findById(attendanceRecordId)
    .orElseThrow(() -> new NotFoundException("Record not found"));

  // 1. Save check-out data
  record.setCheckOutTime(checkOutTime);
  record.setCheckOutLat(lat);
  record.setCheckOutLng(lng);
  
  // 2. Upload photo (async, wait for URL)
  String photoUrl = photoService.uploadPhotoAsync(...)
    .get(10, TimeUnit.SECONDS);
  record.setCheckOutPhotoUrl(photoUrl);

  // 3. **Synchronously calculate status** ← KEY DECISION
  AttendanceStatus status = calculateStatus(record);
  record.setAttendanceStatus(status);

  // 4. **Synchronously calculate OT** (if applicable)
  if (shouldCalculateOt(status)) {
    OtRecord ot = calculateOt(record);
    otRepository.save(ot);
  }

  // 5. Save record and commit transaction
  repo.save(record);

  // 6. Return response immediately (state is finalized)
  return record;
}

private AttendanceStatus calculateStatus(AttendanceRecord record) {
  Shift shift = record.getShift();
  
  // Simple math: compare times against thresholds
  if (isHalfDay(record)) return HALF_DAY;
  if (isLateLateAndEarlyOut(record)) return LATE_IN_EARLY_OUT;
  if (isLateIn(record)) return LATE_IN;
  if (isEarlyOut(record)) return EARLY_OUT;
  return ON_TIME;
}

private OtRecord calculateOt(AttendanceRecord record) {
  Shift shift = record.getShift();
  
  int otMinutes = calculateOtDuration(record);
  if (otMinutes <= 0) return null;

  DayType dayType = determineDayType(record.getDate());
  double multiplier = getMultiplier(dayType);  // 1.5, 2.0, or 3.0

  return new OtRecord(
    record,
    record.getDate(),
    otMinutes,
    dayType,
    multiplier
  );
}
```

### 4.5 Why Not Event-Driven?

```java
// ❌ WRONG: Event-driven approach

@PostMapping("/api/attendance/check-out")
public ResponseEntity<AttendanceRecordDto> checkOut(...) {
  AttendanceRecord record = repo.save(checkoutData);
  
  // Fire event, return immediately
  eventPublisher.publishEvent(new CheckOutEvent(record));
  
  return ResponseEntity.ok(mapper.toDto(record));
  // ← Record doesn't have status yet!
}

@EventListener
@Async
public void onCheckOut(CheckOutEvent event) {
  // Background thread processes status calculation
  // Meanwhile, frontend has received record without status
  // Frontend doesn't know: is it ON_TIME? LATE_IN? Still computing?
  // Race condition: What if calculation fails?
}
```

**Problems:**
1. Frontend gets record without status → must poll or wait
2. Calculation can fail, no error feedback to caller
3. Adds complexity: event bus, handlers, eventual consistency
4. Harder to test and debug

**Sync approach is superior:**
- Caller knows status is correct before response sent
- No eventual consistency issues
- Simple, testable, predictable
- Still fast (status calculation is <100ms)

### 4.6 Exception Handling

If status calculation throws (unlikely):

```java
@Transactional(rollbackFor = Exception.class)
public AttendanceRecord checkOut(...) {
  try {
    // ... collect data, upload photo ...
    AttendanceStatus status = calculateStatus(record);
    record.setAttendanceStatus(status);
    return repo.save(record);
  } catch (StatusCalculationException e) {
    // Should never happen in production
    logger.error("Status calculation failed for record {}", record.getId(), e);
    // Rollback entire transaction, return 500
    throw new InternalServerError("Failed to finalize check-out");
  }
}
```

---

## 5. Decision: Scheduled Jobs (Spring @Scheduled), Not External Scheduler

### 5.1 Problem Statement

Two background tasks must run periodically:
- **IncompleteAttendanceJob:** Every 5 minutes, mark check-ins without check-out as INCOMPLETE (FR-9)
- **AbsentRecordJob:** Daily at 00:05, create ABSENT records for employees who didn't show up (FR-10)

Should these run:
- **Embedded:** Spring Boot `@Scheduled` cron
- **External:** Quartz, Jenkins, separate scheduler service

### 5.2 Decision

**Use Spring Boot `@Scheduled` with `@EnableScheduling`.**

Simple cron expressions, no external dependency.

### 5.3 Rationale

| Technology | Complexity | Reliability | Scaling | Why Not |
|-----------|-----------|-----------|---------|---------|
| **Spring @Scheduled** ✅ | Very Low | High (embedded) | Single instance | **CHOSEN** — Fits MVP |
| Quartz | Medium | Very High | Clustered scheduling | Overkill; adds .jar dependency |
| Kubernetes CronJob | High | High | Distributed | Requires K8s (not in MVP) |
| Scheduled Lambda/Cloud Functions | Medium | Very High | Serverless | Vendor lock-in; out of scope |

### 5.4 Implementation

```java
// scheduler/IncompleteAttendanceJob.java

@Component
public class IncompleteAttendanceJob {

  @Autowired
  private AttendanceRecordRepository repo;

  @Autowired
  private NotificationService notificationService;

  @Scheduled(
    cron = "0 */5 7-22 * * ?",  // Every 5 minutes, 7 AM to 10 PM
    zone = "Asia/Ho_Chi_Minh"   // Always use Vietnam timezone
  )
  public void markIncompleteRecords() {
    LocalDateTime gracePeriodEnd = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
      .minusMinutes(30);  // 30 minute grace period

    List<AttendanceRecord> incomplete = repo.findByStatusAndCheckOutTimeNullAndCheckInTimeBeforeAndCreatedAtBefore(
      AttendanceStatus.ON_TIME,  // Or LATE_IN, EARLY_OUT, etc.
      gracePeriodEnd
    );

    for (AttendanceRecord record : incomplete) {
      record.setAttendanceStatus(AttendanceStatus.INCOMPLETE);
      record.setApprovalSubStatus(ApprovalSubStatus.PENDING_ADJUSTMENT);
      repo.save(record);

      // Notify employee
      notificationService.notifyIncompleteRecord(record);
    }
  }
}

// scheduler/AbsentRecordJob.java

@Component
public class AbsentRecordJob {

  @Scheduled(
    cron = "0 5 0 * * ?",  // 00:05 every day
    zone = "Asia/Ho_Chi_Minh"
  )
  public void createAbsentRecords() {
    LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
      .minusDays(1);

    // Get all active employees
    List<User> employees = userRepository.findByIsActiveTrue();

    // Get employees who checked in yesterday
    List<String> presentEmployeeIds = repo.findByDate(yesterday)
      .stream()
      .map(AttendanceRecord::getEmployeeId)
      .collect(toSet());

    // Create ABSENT for missing ones
    for (User employee : employees) {
      if (!presentEmployeeIds.contains(employee.getId())) {
        AttendanceRecord absent = new AttendanceRecord();
        absent.setEmployeeId(employee.getId());
        absent.setDate(yesterday);
        absent.setAttendanceStatus(AttendanceStatus.ABSENT);
        repo.save(absent);
      }
    }
  }
}

// config/SchedulerConfig.java

@Configuration
@EnableScheduling
public class SchedulerConfig {
  // @Scheduled beans are auto-discovered and registered
}
```

### 5.5 Timezone Safety

**Critical:** Cron expressions must use Vietnam timezone, not server timezone.

```java
// ✅ CORRECT
@Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Ho_Chi_Minh")

// ❌ WRONG (uses server timezone, not Vietnam)
@Scheduled(cron = "0 5 0 * * ?")  // Defaults to ZoneId.systemDefault()
```

### 5.6 Idempotency

Both jobs must be **idempotent** (safe to run multiple times):

```java
// IncompleteAttendanceJob:
// - Query: WHERE status != INCOMPLETE AND checkOutTime IS NULL AND checkInTime < gracePeriodEnd
// - This query will return the same set of records if run twice
// - Setting status to INCOMPLETE twice is harmless (idempotent update)

// AbsentRecordJob:
// - Query: WHERE date = yesterday AND employeeId NOT IN (...)
// - Create ABSENT record only if not already present
// - UNIQUE KEY (employee_id, date) prevents duplicates
```

### 5.7 Single-Instance Limitation

**Important:** Spring @Scheduled runs on a single instance. If you scale to multiple backend instances:

```
Instance 1: Spring Backend
  ├─ IncompleteAttendanceJob runs at 07:05
  └─ AbsentRecordJob runs at 00:05

Instance 2: Spring Backend (duplicate!)
  ├─ IncompleteAttendanceJob runs at 07:05 (duplicate!)
  └─ AbsentRecordJob runs at 00:05 (duplicate!)

Result: Duplicate job execution (harmless due to idempotency, but wasteful)
```

**Solution for v1.1:** Use Quartz with shared database lock:

```java
@Bean(name = "quartzScheduler")
public Scheduler quartzScheduler() {
  // Quartz with database persistence + clustering
  // Ensures only one instance runs job at a time
}
```

For MVP (single instance), Spring @Scheduled is perfect.

### 5.8 Monitoring

```java
@Scheduled(cron = "0 */5 7-22 * * ?", zone = "Asia/Ho_Chi_Minh")
public void markIncompleteRecords() {
  long startTime = System.currentTimeMillis();
  
  try {
    List<AttendanceRecord> records = repo.findIncomplete(...);
    logger.info("Found {} incomplete records", records.size());
    
    for (AttendanceRecord record : records) {
      record.setAttendanceStatus(INCOMPLETE);
      repo.save(record);
    }
    
    long duration = System.currentTimeMillis() - startTime;
    logger.info("IncompleteAttendanceJob completed in {}ms", duration);
  } catch (Exception e) {
    logger.error("IncompleteAttendanceJob failed", e);
    // Alert ops (optional: send email/Slack notification)
    alertService.sendAlert("IncompleteAttendanceJob failed: " + e.getMessage());
  }
}
```

---

## 6. Decision: No Caching (v1.0), Prepare for Redis (v1.1)

### 6.1 Problem Statement

Some data doesn't change often:
- Shifts configuration (rarely updated)
- Holidays (set once per year)
- User roles (infrequently changed)

Caching could reduce database hits. But:
- Adds complexity (invalidation, staleness)
- Increases memory usage
- Requires cache coherence across instances

### 6.2 Decision (v1.0)

**No caching for MVP. Hit database directly.**

Shifts/holidays are queried infrequently enough. MySQL with proper indexes is sufficient.

### 6.3 Rationale

| Approach | Performance | Complexity | Cost | Why Not |
|----------|-----------|-----------|------|---------|
| **No cache (SQL only)** ✅ | ~10ms queries | Very Low | $0 | **CHOSEN** — Fast enough for MVP |
| Application-level cache (Caffeine) | ~1ms | Low | ~50MB memory | Doesn't scale to multiple instances |
| Redis cache | ~5ms | Medium | $10–50/month | Adds external dependency, DevOps |

### 6.4 Future: Prepare for Redis (v1.1)

```java
// v1.1 implementation (not in MVP):

@Configuration
@EnableCaching
public class CacheConfig {
  @Bean
  public CacheManager cacheManager(LettuceConnectionFactory factory) {
    return RedisCacheManager.factory(factory).build();
  }
}

@Service
public class ShiftService {

  @Cacheable(value = "shifts", key = "#id")
  public Shift getShiftById(String id) {
    return repo.findById(id).orElseThrow(...);
  }

  @CachePut(value = "shifts", key = "#shift.id")
  public Shift updateShift(Shift shift) {
    return repo.save(shift);
  }

  @CacheEvict(value = "shifts", allEntries = true)
  public void clearShiftCache() {
    // On startup or manual admin action
  }
}
```

For MVP: Skip this. MVP is a single instance, and shift queries are fast.

---

## 7. Summary Table: Key Decisions

| Decision | Choice | Rationale | Alternative |
|----------|--------|-----------|-------------|
| **Authentication** | JWT Bearer | Stateless, scalable | Sessions, OAuth |
| **Notifications** | HTTP Polling (15s) | Simple, no DevOps | WebSocket, SSE |
| **Photo Upload** | Async CompletableFuture | Non-blocking, reliable | Sync, fire-and-forget |
| **State Calculation** | Synchronous | Consistent, fast | Event-driven |
| **Scheduled Jobs** | Spring @Scheduled | Embedded, zero-config | Quartz, external scheduler |
| **Caching** | None (v1.0) | YAGNI (not needed yet) | Redis, Caffeine |
| **Session Storage** | Stateless JWT | No session state | Tomcat session, Redis |
| **Job Distribution** | Single instance | Sufficient for MVP | Quartz clustering |

---

## 8. Trade-offs Accepted

| Trade-off | Acceptance | Reason |
|-----------|-----------|--------|
| Token revocation is delayed (15 min) | Accepted | TTL mitigates risk; immediate revocation can be added in v1.1 |
| 15s notification latency | Accepted | Acceptable for HR workflow; WebSocket can be added later |
| Single-instance scheduling | Accepted | MVP is single instance; Quartz clustering for v1.1 |
| No advanced caching | Accepted | Database queries are fast with indexes; Redis adds DevOps cost |
| No audit trail for notification delivery | Accepted | Notifications are non-critical; staff can manually check |
| Photo compression on backend | Accepted | Ensures consistent quality; client-side compression is flaky |

---

## 9. Version Roadmap

| Version | Enhancement | Decision Implication |
|---------|-----------|----------------------|
| **v1.0 (MVP)** | JWT, polling, sync state, @Scheduled | This ADR |
| **v1.1 (Next)** | Redis caching, WebSocket optional | Add CacheManager config |
| **v1.2** | Quartz clustering, distributed jobs | Add Quartz DB config |
| **v2.0** | Kubernetes, multi-region | Re-evaluate scheduler & cache |

---

## 10. References

- [RFC 7519: JSON Web Tokens (JWT)](https://tools.ietf.org/html/rfc7519)
- [Spring Security: JWT Authentication](https://spring.io/blog/2015/01/12/spring-and-http-session-8-http-session-replication-redux)
- [Spring `@Scheduled` Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling)
- [TanStack Query Polling](https://tanstack.com/query/latest/docs/react/guides/important-defaults#stale-time)
- [Java CompletableFuture Pattern](https://www.baeldung.com/java-completablefuture)

---

**Status:** ✅ Approved  
**Last Updated:** 2026-06-04  
**Architecture Lead:** Claude Code (BMAD)  
**Review Date:** 2026-07-04 (v1.1 planning)
