# Database Schema — MySQL 8.0

**Database:** `itx_attendance`  
**Charset:** `utf8mb4`  
**Collation:** `utf8mb4_unicode_ci`  
**Timezone:** All `TIMESTAMP` columns stored as UTC; application layer converts to UTC+7

---

## 1. Core Tables

### 1.1 `users` (Employee, Leader, Admin)

**Purpose:** User authentication and role-based access control  
**Related PRD:** Authentication (§4.6), RBAC in project_context

```sql
CREATE TABLE users (
  id CHAR(36) PRIMARY KEY COMMENT 'UUID',
  
  -- Authentication
  username VARCHAR(50) UNIQUE NOT NULL COMMENT 'Login username',
  email VARCHAR(100) UNIQUE NOT NULL COMMENT 'Email, used for notifications',
  password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
  
  -- Profile
  full_name VARCHAR(100) NOT NULL COMMENT 'Full name in Vietnamese',
  avatar_url VARCHAR(500) COMMENT 'Profile photo URL',
  department VARCHAR(100) COMMENT 'Department/Team',
  employee_code VARCHAR(20) UNIQUE COMMENT 'HR employee ID',
  
  -- Assignment
  shift_id CHAR(36) COMMENT 'FK → shifts (current shift)',
  leader_id CHAR(36) COMMENT 'FK → users (their Team Leader, for EMPLOYEE role)',
  
  -- Authorization
  role ENUM('EMPLOYEE', 'LEADER', 'ADMIN') NOT NULL DEFAULT 'EMPLOYEE',
  is_active BOOLEAN NOT NULL DEFAULT true,
  
  -- Metadata
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  last_login_at TIMESTAMP,
  
  FOREIGN KEY (shift_id) REFERENCES shifts(id),
  FOREIGN KEY (leader_id) REFERENCES users(id),
  
  KEY idx_users_role (role),
  KEY idx_users_leader_id (leader_id),
  KEY idx_users_is_active (is_active)
) COMMENT='User profiles: Employee, Leader, Admin';
```

---

### 1.2 `shifts` (Ca làm việc)

**Purpose:** Working shift configuration  
**Related PRD:** FR-6 (Cấu hình Ca Cố định)

```sql
CREATE TABLE shifts (
  id CHAR(36) PRIMARY KEY COMMENT 'UUID',
  
  -- Shift Identity
  name VARCHAR(100) NOT NULL UNIQUE COMMENT 'E.g., "Ca Sáng", "Ca Chiều"',
  description TEXT,
  
  -- Time Configuration (in UTC+7, stored as TIME)
  start_time TIME NOT NULL COMMENT 'Giờ bắt đầu ca (HH:MM:SS)',
  end_time TIME NOT NULL COMMENT 'Giờ kết thúc ca (HH:MM:SS)',
  
  -- Check-in/out Flexibility
  checkin_open_before INT DEFAULT 30 COMMENT 'Allow check-in N minutes before shift_start',
  
  -- Threshold Configuration (in minutes)
  late_in_threshold INT NOT NULL DEFAULT 0 COMMENT 'Minutes late after shift_start before marking LATE_IN',
  early_out_threshold INT NOT NULL DEFAULT 0 COMMENT 'Minutes early before shift_end before marking EARLY_OUT',
  half_day_threshold INT NOT NULL DEFAULT 30 COMMENT 'Threshold for HALF_DAY status (minutes)',
  
  -- OT Configuration
  ot_buffer INT NOT NULL DEFAULT 30 COMMENT 'Minutes after shift_end before OT calculation starts',
  grace_period INT NOT NULL DEFAULT 30 COMMENT 'Grace period for INCOMPLETE detection (minutes)',
  
  -- Status
  is_active BOOLEAN NOT NULL DEFAULT true,
  
  -- Metadata
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by CHAR(36) COMMENT 'FK → users (admin who created)',
  
  KEY idx_shifts_is_active (is_active),
  KEY idx_shifts_name (name)
) COMMENT='Shift configurations (Fixed-time only in MVP)';
```

---

### 1.3 `attendance_records` (Bản ghi Chấm công)

**Purpose:** Core attendance data — one record per employee per day  
**Related PRD:** State Machine (§5), FR-1 to FR-10, FR-20

```sql
CREATE TABLE attendance_records (
  id CHAR(36) PRIMARY KEY COMMENT 'UUID',
  
  -- Employee & Shift
  employee_id CHAR(36) NOT NULL COMMENT 'FK → users',
  shift_id CHAR(36) NOT NULL COMMENT 'FK → shifts (snapshot at check-in time)',
  
  -- Date
  date DATE NOT NULL COMMENT 'Ngày làm việc (UTC+7)',
  
  -- Check-in Data
  check_in_time TIMESTAMP COMMENT 'Check-in timestamp (UTC)',
  check_in_ip VARCHAR(45) COMMENT 'Public IP address at check-in (IPv4 or IPv6)',
  check_in_lat DECIMAL(9, 6) COMMENT 'GPS latitude at check-in',
  check_in_lng DECIMAL(9, 6) COMMENT 'GPS longitude at check-in',
  check_in_photo_url VARCHAR(500) COMMENT 'S3 presigned URL for check-in photo',
  
  -- Check-out Data
  check_out_time TIMESTAMP COMMENT 'Check-out timestamp (UTC), nullable if not checked out',
  check_out_ip VARCHAR(45) COMMENT 'Public IP address at check-out (IPv4 or IPv6)',
  check_out_lat DECIMAL(9, 6) COMMENT 'GPS latitude at check-out',
  check_out_lng DECIMAL(9, 6) COMMENT 'GPS longitude at check-out',
  check_out_photo_url VARCHAR(500) COMMENT 'S3 presigned URL for check-out photo',
  
  -- Flags
  gps_unavailable BOOLEAN DEFAULT false COMMENT 'GPS was unavailable during check-in (office mode allowed)',
  is_client_site BOOLEAN DEFAULT false COMMENT 'Check-in outside office (IP not required)',
  suspicious_location BOOLEAN DEFAULT false COMMENT 'Fraud flag: unusual GPS distance (FR-5)',
  is_admin_override BOOLEAN DEFAULT false COMMENT 'Record was manually edited by Admin (FR-20)',
  
  -- Status (Main)
  attendance_status ENUM(
    'ON_TIME',
    'LATE_IN',
    'EARLY_OUT',
    'LATE_IN_EARLY_OUT',
    'HALF_DAY',
    'INCOMPLETE',
    'ABSENT'
  ) NOT NULL COMMENT 'State Machine main status (§5 PRD)',
  
  -- Sub-status (Approval Overlay)
  approval_sub_status ENUM(
    NULL,
    'PENDING_APPROVAL',
    'PENDING_ADJUSTMENT',
    'APPROVED',
    'REJECTED',
    'ADMIN_OVERRIDE'
  ) DEFAULT NULL COMMENT 'Overlay status for request/approval flow',
  
  -- Metadata
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When check-in was recorded',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'When any update happened',
  
  -- Optimistic Locking
  version BIGINT DEFAULT 0 COMMENT 'For concurrent update safety',
  
  FOREIGN KEY (employee_id) REFERENCES users(id),
  FOREIGN KEY (shift_id) REFERENCES shifts(id),
  
  -- Indexes for quick queries
  UNIQUE KEY uk_attendance_employee_date (employee_id, date),
  KEY idx_attendance_status (attendance_status),
  KEY idx_attendance_date (date),
  KEY idx_attendance_created_at (created_at),
  KEY idx_attendance_check_in_time (check_in_time)
) COMMENT='Attendance records: check-in/out with status calculation';
```

---

### 1.4 `exception_requests` (Yêu cầu Ngoại lệ)

**Purpose:** Employee requests to override violations (LATE_IN, EARLY_OUT, HALF_DAY)  
**Related PRD:** FR-11 (Gửi Yêu cầu Ngoại lệ), FR-13 (Phê duyệt)

```sql
CREATE TABLE exception_requests (
  id CHAR(36) PRIMARY KEY COMMENT 'UUID',
  
  -- Link to attendance
  attendance_record_id CHAR(36) NOT NULL UNIQUE COMMENT 'FK → attendance_records (one request per record)',
  employee_id CHAR(36) NOT NULL COMMENT 'FK → users (for quick filtering)',
  
  -- Request Details
  request_type ENUM('LATE_IN', 'EARLY_OUT', 'HALF_DAY') NOT NULL COMMENT 'Type of violation being appealed',
  reason TEXT NOT NULL COMMENT 'Why employee is requesting exception (bắt buộc, không rỗng)',
  
  -- Approval
  status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
  reviewed_by CHAR(36) COMMENT 'FK → users (Leader/Admin who reviewed)',
  review_reason TEXT COMMENT 'Why rejected (bắt buộc nếu REJECTED)',
  
  -- Metadata
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  FOREIGN KEY (attendance_record_id) REFERENCES attendance_records(id) ON DELETE CASCADE,
  FOREIGN KEY (employee_id) REFERENCES users(id),
  FOREIGN KEY (reviewed_by) REFERENCES users(id),
  
  KEY idx_exception_employee_status (employee_id, status),
  KEY idx_exception_created_at (created_at)
) COMMENT='Exception requests to override attendance violations';
```

---

### 1.5 `adjustment_requests` (Yêu cầu Điều chỉnh)

**Purpose:** Employee requests to fix INCOMPLETE records (missing check-out)  
**Related PRD:** FR-12 (Gửi Yêu cầu Điều chỉnh)

```sql
CREATE TABLE adjustment_requests (
  id CHAR(36) PRIMARY KEY COMMENT 'UUID',
  
  -- Link to attendance
  attendance_record_id CHAR(36) NOT NULL UNIQUE COMMENT 'FK → attendance_records (one adjustment per record)',
  employee_id CHAR(36) NOT NULL COMMENT 'FK → users',
  
  -- Proposed Data
  proposed_checkout_time TIMESTAMP NOT NULL COMMENT 'Proposed check-out time (must be > check_in_time)',
  reason TEXT NOT NULL COMMENT 'Why check-out was missed',
  
  -- Approval
  status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
  reviewed_by CHAR(36) COMMENT 'FK → users (Leader/Admin)',
  review_reason TEXT,
  
  -- Metadata
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  FOREIGN KEY (attendance_record_id) REFERENCES attendance_records(id) ON DELETE CASCADE,
  FOREIGN KEY (employee_id) REFERENCES users(id),
  FOREIGN KEY (reviewed_by) REFERENCES users(id),
  
  KEY idx_adjustment_employee_status (employee_id, status),
  KEY idx_adjustment_created_at (created_at)
) COMMENT='Adjustment requests for INCOMPLETE records';
```

---

## 2. Configuration Tables

### 2.1 `valid_ips` (Public IP hợp lệ)

**Purpose:** Whitelist of office Public IP addresses for office check-in validation  
**Related PRD:** FR-1 (Xác thực Public IP), FR-19 (Quản lý IP) — xem ADR-002

```sql
CREATE TABLE valid_ips (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  
  -- IP Address
  ip_address VARCHAR(45) NOT NULL COMMENT 'IPv4 hoặc IPv6 public IP của văn phòng',
  
  -- Scope
  scope ENUM('COMPANY', 'INDIVIDUAL') NOT NULL DEFAULT 'COMPANY' COMMENT 'Company-wide or employee-specific',
  employee_id CHAR(36) COMMENT 'FK → users (null for COMPANY scope)',
  
  -- Metadata
  description VARCHAR(255) COMMENT 'Notes: e.g., "Văn phòng HCM", "IP dự phòng"',
  created_by CHAR(36) NOT NULL COMMENT 'FK → users (admin)',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  -- Audit
  is_active BOOLEAN DEFAULT true,
  
  FOREIGN KEY (employee_id) REFERENCES users(id),
  FOREIGN KEY (created_by) REFERENCES users(id),
  
  UNIQUE KEY uk_ip_scope (ip_address, scope, employee_id),
  KEY idx_ip_scope (scope),
  KEY idx_ip_employee (employee_id),
  KEY idx_ip_is_active (is_active)
) COMMENT='Whitelist of valid office Public IP addresses';
```

---

### 2.2 `holidays` (Ngày lễ)

**Purpose:** Fixed and dynamic holidays for OT calculation  
**Related PRD:** FR-17 (Fixed Holidays), FR-18 (Dynamic Holidays), FR-16 (OT Classification)

```sql
CREATE TABLE holidays (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  
  -- Date
  date DATE NOT NULL COMMENT 'Holiday date (Dương lịch)',
  name VARCHAR(100) NOT NULL COMMENT 'Holiday name (Vietnamese)',
  
  -- Type
  type ENUM('FIXED', 'DYNAMIC') NOT NULL COMMENT 'FIXED: annual (1/1, 30/4, etc.); DYNAMIC: Lunar-based',
  year INT NOT NULL COMMENT 'Year this holiday applies to',
  
  -- Metadata
  description TEXT,
  created_by CHAR(36) COMMENT 'FK → users (admin)',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  is_active BOOLEAN DEFAULT true,
  
  FOREIGN KEY (created_by) REFERENCES users(id),
  
  UNIQUE KEY uk_holiday_date_year (date, year),
  KEY idx_holiday_type (type),
  KEY idx_holiday_year (year)
) COMMENT='Holiday calendar for OT multiplier classification';
```

---

## 3. OT & Reporting Tables

### 3.1 `ot_records` (Ghi nhận OT)

**Purpose:** Track overtime, calculated and stored after each check-out  
**Related PRD:** FR-15 (Tính giờ OT), FR-16 (Phân loại Hệ số OT)

```sql
CREATE TABLE ot_records (
  id CHAR(36) PRIMARY KEY COMMENT 'UUID',
  
  -- Reference
  attendance_record_id CHAR(36) NOT NULL COMMENT 'FK → attendance_records',
  employee_id CHAR(36) NOT NULL COMMENT 'FK → users',
  
  -- Date & Duration
  date DATE NOT NULL COMMENT 'Date of OT (same as attendance date)',
  ot_duration_minutes INT NOT NULL COMMENT 'Total OT duration in minutes',
  
  -- Classification
  day_type ENUM('WEEKDAY', 'WEEKEND', 'HOLIDAY') NOT NULL COMMENT 'Day type for multiplier',
  ot_multiplier DECIMAL(3, 1) NOT NULL COMMENT 'Multiplier: 1.5 (weekday), 2.0 (weekend), 3.0 (holiday)',
  
  -- Calculated Fields (for reporting)
  ot_hours DECIMAL(5, 2) GENERATED ALWAYS AS (ot_duration_minutes / 60) STORED,
  
  -- Metadata
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  FOREIGN KEY (attendance_record_id) REFERENCES attendance_records(id) ON DELETE CASCADE,
  FOREIGN KEY (employee_id) REFERENCES users(id),
  
  KEY idx_ot_employee_date (employee_id, date),
  KEY idx_ot_created_at (created_at)
) COMMENT='OT calculation records';
```

---

## 4. Notification & Audit Tables

### 4.1 `notifications` (Thông báo In-app)

**Purpose:** Store in-app notifications for polling via `/api/notifications/pending`  
**Related PRD:** FR-14 (Notification Service)

```sql
CREATE TABLE notifications (
  id CHAR(36) PRIMARY KEY COMMENT 'UUID',
  
  -- Recipient
  recipient_id CHAR(36) NOT NULL COMMENT 'FK → users (Leader/Admin)',
  
  -- Content
  title VARCHAR(255) NOT NULL COMMENT 'Notification title (Vietnamese)',
  message TEXT NOT NULL COMMENT 'Notification body',
  notification_type ENUM(
    'REQUEST_CREATED',
    'REQUEST_APPROVED',
    'REQUEST_REJECTED',
    'INCOMPLETE_RECORD',
    'ABSENT_RECORD',
    'SYSTEM_ALERT'
  ) NOT NULL,
  
  -- Reference (optional)
  related_record_id CHAR(36) COMMENT 'FK → attendance_records, exception_requests, etc.',
  
  -- Status
  is_read BOOLEAN DEFAULT false,
  
  -- Metadata
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  FOREIGN KEY (recipient_id) REFERENCES users(id),
  
  KEY idx_notification_recipient_read (recipient_id, is_read),
  KEY idx_notification_created_at (created_at DESC)
) COMMENT='In-app notifications (polling-based, not WebSocket)';
```

---

### 4.2 `audit_logs` (Nhật ký Kiểm toán)

**Purpose:** Immutable log of all admin changes  
**Related PRD:** FR-20 (Override Thủ công)

```sql
CREATE TABLE audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Sequential ID',
  
  -- Actor
  admin_id CHAR(36) NOT NULL COMMENT 'FK → users (admin who made change)',
  
  -- Change Target
  target_table VARCHAR(50) NOT NULL COMMENT 'Table that was modified (attendance_records, shifts, etc.)',
  target_id VARCHAR(50) NOT NULL COMMENT 'ID of record that was modified',
  
  -- Change Details
  field_changed VARCHAR(100) NOT NULL COMMENT 'Column name that changed',
  old_value TEXT COMMENT 'Previous value (may be large, e.g., photo URL)',
  new_value TEXT COMMENT 'New value',
  
  -- Reason (mandatory)
  reason TEXT NOT NULL COMMENT 'Why this change was made (bắt buộc, không được rỗng)',
  
  -- Timestamp (immutable)
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When change occurred (no updated_at)',
  
  FOREIGN KEY (admin_id) REFERENCES users(id),
  
  KEY idx_audit_admin_id (admin_id),
  KEY idx_audit_target_table (target_table),
  KEY idx_audit_created_at (created_at DESC),
  KEY idx_audit_target (target_table, target_id)
) COMMENT='Immutable audit log (no UPDATE/DELETE allowed)';
```

---

## 5. Indexes for Performance

### 5.1 Critical Indexes (for SM-5, SM-6 from PRD)

```sql
-- Attendance lookup by employee + date (most frequent query)
CREATE INDEX idx_attendance_employee_date ON attendance_records(employee_id, date);

-- Notification polling (Leader gets unread notifications)
CREATE INDEX idx_notification_recipient_read ON notifications(recipient_id, is_read);

-- Request approval queue (Leader views pending requests)
CREATE INDEX idx_exception_employee_status ON exception_requests(employee_id, status);
CREATE INDEX idx_adjustment_employee_status ON adjustment_requests(employee_id, status);

-- Scheduled job queries (find INCOMPLETE, ABSENT)
CREATE INDEX idx_attendance_status ON attendance_records(attendance_status);
CREATE INDEX idx_attendance_check_in_time ON attendance_records(check_in_time);

-- Audit log filtering
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);
```

---

## 6. Sample Data (Seed)

### 6.1 Flyway Migration: `V001__Initial_Schema.sql`

This file is auto-executed by Flyway on application startup.

```sql
-- File: src/main/resources/db/migration/V001__Initial_Schema.sql

-- Create users table
CREATE TABLE users (
  id CHAR(36) PRIMARY KEY,
  username VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(100) NOT NULL,
  avatar_url VARCHAR(500),
  department VARCHAR(100),
  employee_code VARCHAR(20) UNIQUE,
  shift_id CHAR(36),
  leader_id CHAR(36),
  role ENUM('EMPLOYEE', 'LEADER', 'ADMIN') NOT NULL DEFAULT 'EMPLOYEE',
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  last_login_at TIMESTAMP,
  FOREIGN KEY (shift_id) REFERENCES shifts(id),
  FOREIGN KEY (leader_id) REFERENCES users(id),
  KEY idx_users_role (role),
  KEY idx_users_leader_id (leader_id),
  KEY idx_users_is_active (is_active)
) CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create shifts table
CREATE TABLE shifts (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE,
  description TEXT,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  checkin_open_before INT DEFAULT 30,
  late_in_threshold INT NOT NULL DEFAULT 0,
  early_out_threshold INT NOT NULL DEFAULT 0,
  half_day_threshold INT NOT NULL DEFAULT 30,
  ot_buffer INT NOT NULL DEFAULT 30,
  grace_period INT NOT NULL DEFAULT 30,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by CHAR(36),
  FOREIGN KEY (created_by) REFERENCES users(id),
  KEY idx_shifts_is_active (is_active),
  KEY idx_shifts_name (name)
) CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- [Continue with all other tables...]

-- Insert seed data
INSERT INTO shifts (id, name, start_time, end_time, late_in_threshold, early_out_threshold, half_day_threshold, ot_buffer, grace_period) VALUES
  (UUID(), 'Ca Sáng', '09:00:00', '17:30:00', 0, 0, 30, 30, 30),
  (UUID(), 'Ca Chiều', '14:00:00', '22:00:00', 0, 0, 30, 30, 30);

INSERT INTO holidays (date, name, type, year) VALUES
  ('2026-01-01', 'Tết Dương Lịch', 'FIXED', 2026),
  ('2026-04-30', 'Giải Phóng Miền Nam', 'FIXED', 2026),
  ('2026-05-01', 'Quốc Tế Lao Động', 'FIXED', 2026),
  ('2026-09-02', 'Quốc Khánh', 'FIXED', 2026);
```

### 6.2 Flyway Versioning

```
src/main/resources/db/migration/
├── V001__Initial_Schema.sql          (Tables: users, shifts, attendance_records, etc.)
├── V002__Add_Indexes.sql             (Performance-critical indexes)
├── V003__Add_Audit_Log.sql           (audit_logs table)
├── V004__Seed_Default_Holidays.sql   (Initial holiday data)
└── V005__Add_OT_Records.sql          (OT calculation tables)
```

---

## 7. Data Consistency Rules

### 7.1 Referential Integrity

| Constraint | Rule | Reason |
|-----------|------|--------|
| `attendance_records` → `users` | NO DELETE | Keep audit trail |
| `attendance_records` → `shifts` | NO DELETE | Keep configuration snapshot |
| `exceptions_requests` → `attendance_records` | CASCADE DELETE | Clean up orphaned requests |
| `audit_logs` → `users` | NO DELETE | Keep audit trail immutable |

### 7.2 Unique Constraints

- `users (username, email, employee_code)` — No duplicates
- `shifts (name)` — Unique shift names
- `attendance_records (employee_id, date)` — One record per employee per day
- `valid_ips (ip_address, scope, employee_id)` — No duplicate IPs in same scope
- `exception_requests (attendance_record_id)` — One request per record
- `adjustment_requests (attendance_record_id)` — One adjustment per record
- `holidays (date, year)` — No duplicate holidays per year

### 7.3 Validation Rules

| Column | Rule | Implementation |
|--------|------|---|
| `check_out_time` | Must be ≥ `check_in_time` | Application logic + DB check |
| `proposed_checkout_time` (adjustment) | Must be > `check_in_time` | Application validation |
| `reason` (requests) | Not empty, min 10 chars (optional) | Application @NotBlank |
| `ip_address` | Valid IPv4 or IPv6 format | Application @Pattern (InetAddressValidator) |
| `date` | Cannot be in future | Application LocalDate.now() |
| `late_in_threshold`, etc. | ≥ 0 | Application @Min(0) |
| `ot_buffer`, `grace_period` | ≥ 0 | Application @Min(0) |

---

## 8. Database Timezone & Time Handling

### 8.1 UTC Storage, UTC+7 Display

```typescript
// Frontend (JavaScript)
const utcTime = attendance.checkInTime;  // "2026-06-04T08:45:00Z" (ISO8601)
const vietTime = new Date(utcTime).toLocaleString('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit'
});
// Display: "04/06/2026, 08:45:00"

// Backend (Java)
LocalDateTime vietTime = utcTime.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
  .toLocalDateTime();
// Or use Hibernate's @TimeZoneStorage(TimeZoneStorageType.NATIVE)
```

### 8.2 Scheduled Jobs Timezone

```java
@Scheduled(
  cron = "0 */5 7-22 * * ?",  // Every 5 minutes, 7 AM to 10 PM
  zone = "Asia/Ho_Chi_Minh"    // Always use Vietnam timezone
)
public void markIncompleteRecords() { ... }
```

---

## 9. Monitoring Queries (for Admin Dashboard)

### 9.1 Daily Attendance Summary

```sql
SELECT
  DATE(CONVERT_TZ(created_at, '+00:00', '+07:00')) as day,
  attendance_status,
  COUNT(*) as count
FROM attendance_records
WHERE DATE(CONVERT_TZ(created_at, '+00:00', '+07:00')) = CURDATE()
GROUP BY day, attendance_status;
```

### 9.2 Pending Requests

```sql
SELECT
  e.full_name,
  er.request_type,
  er.reason,
  er.created_at
FROM exception_requests er
JOIN users e ON er.employee_id = e.id
WHERE er.status = 'PENDING'
ORDER BY er.created_at DESC;
```

### 9.3 Audit Trail for Employee

```sql
SELECT
  a.admin_id,
  a.field_changed,
  a.old_value,
  a.new_value,
  a.reason,
  a.created_at
FROM audit_logs a
WHERE a.target_table = 'attendance_records'
  AND a.target_id = ?
ORDER BY a.created_at DESC;
```

---

## 10. Backup & Recovery Strategy

### 10.1 MySQL Backup (Docker-based)

```bash
# Backup
docker exec itx-mysql mysqldump -uroot -ppassword itx_attendance > backup.sql

# Restore
docker exec -i itx-mysql mysql -uroot -ppassword itx_attendance < backup.sql
```

### 10.2 Data Retention

| Data Type | Retention | Reason |
|-----------|-----------|--------|
| Attendance records | 5 years (minimum per Vietnamese labor law) | Audit trail |
| Photos | 5 years | Evidence, archive |
| Audit logs | Indefinite | Legal compliance |
| Notifications | 90 days | Auto-purge (not critical) |
| OT records | 5 years | Payroll archive |

---

**Last Updated:** 2026-06-04  
**DBA/Architect:** Claude Code (BMAD)  
**Status:** Draft v1.0 — Ready for Flyway Migration Setup
