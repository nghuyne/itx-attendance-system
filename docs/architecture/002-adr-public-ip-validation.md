# ADR-002: Pivot từ Xác thực MAC Wi-Fi sang Xác thực Public IP

**Status:** Accepted  
**Date:** 2026-06-04  
**Supersedes:** FR-1 (MAC Wi-Fi validation) trong PRD  
**Context:** Implementation Readiness Review phát hiện Web Browser API không thể đọc MAC Address  
**Audience:** Backend/Frontend engineers, architects, QA

---

## 1. Bối cảnh (Context)

### 1.1 Vấn đề Phát hiện

Trong quá trình Implementation Readiness Review (2026-06-04), nhóm kỹ thuật xác định rằng:

> **Web Browser API không cho phép đọc MAC Address của Wi-Fi adapter.**

Cụ thể:
- **Chrome, Firefox, Safari, Edge** đều không expose MAC address qua Web API chuẩn.
- Các API như `navigator.connection` và `NetworkInformation API` không trả về MAC address.
- Phương án dùng Native App (Electron/Capacitor) để đọc MAC qua OS-level API bị loại vì:
  - Tăng độ phức tạp DevOps (build pipeline riêng, App Store distribution).
  - Mâu thuẫn với quyết định chọn Progressive Web App (PWA) trong ADR-001.
  - Yêu cầu cài đặt thêm phần mềm trên máy nhân viên (out of scope MVP).

### 1.2 Yêu cầu Gốc (Original Requirement)

FR-1 ban đầu yêu cầu:
> "Nhân viên chỉ có thể hoàn thành check-in tại văn phòng nếu địa chỉ MAC của mạng Wi-Fi đang kết nối khớp với ít nhất một mục trong danh sách MAC hợp lệ."

Mục tiêu kinh doanh thực sự là: **Đảm bảo nhân viên đang ở trong văn phòng (hoặc mạng nội bộ công ty) khi chấm công Office Mode.**

MAC address là *phương tiện* để đạt mục tiêu đó, không phải mục tiêu chính.

---

## 2. Quyết định (Decision)

**Thay thế xác thực MAC Wi-Fi bằng xác thực Public IP của mạng văn phòng.**

Thay vì kiểm tra MAC address của Wi-Fi adapter, backend sẽ:
1. Đọc Public IP của request (từ `X-Forwarded-For` header hoặc `HttpServletRequest.getRemoteAddr()`).
2. So sánh với danh sách Public IP hợp lệ trong bảng `valid_ips`.
3. Nếu khớp → cho phép check-in Office Mode.
4. Nếu không khớp → từ chối với lỗi `"INVALID_IP"`.

---

## 3. Phân tích Lựa chọn (Alternatives Considered)

| Phương án | Ưu điểm | Nhược điểm | Kết quả |
|-----------|---------|-----------|---------|
| **Public IP Validation** ✅ | Không cần client-side API đặc biệt; Backend đọc IP từ HTTP request; Dễ quản lý (whitelist IP) | IP có thể thay đổi nếu ISP cấp phát dynamic IP; Không phân biệt được từng thiết bị | **CHỌN** — Đủ tốt cho MVP, dễ triển khai |
| MAC Address (Native App) | Chính xác nhất — định danh phần cứng | Yêu cầu cài app riêng; Out of scope MVP; Phức tạp DevOps | Loại — vi phạm constraint Web-based |
| GPS Only | Đã có trong hệ thống; Không cần thêm gì | GPS không đủ chính xác trong tòa nhà; Dễ bị giả mạo (fake GPS app) | Loại — không đủ bảo mật cho Office Mode |
| VPN + Network Access | Rất mạnh về bảo mật | Yêu cầu hạ tầng VPN; Phức tạp cho nhân viên | Loại — Out of scope MVP |
| Browser Fingerprint | Không cần cài thêm gì | Dễ bypass; Không đáng tin cậy | Loại — không đủ bảo mật |

---

## 4. Chi tiết Triển khai (Implementation Details)

### 4.1 Backend: Đọc Public IP

```java
// util/IpAddressResolver.java
@Component
public class IpAddressResolver {

  public String resolve(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      // X-Forwarded-For: client, proxy1, proxy2
      // Take the first (leftmost) IP = actual client IP
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
```

### 4.2 Backend: Xác thực IP

```java
// service/IpValidationService.java
@Service
public class IpValidationService {

  @Autowired
  private ValidIpRepository validIpRepository;

  public boolean isValidOfficeIp(String publicIp, String employeeId) {
    // Check COMPANY scope (applies to all employees)
    boolean companyMatch = validIpRepository
      .existsByIpAddressAndScopeAndIsActive(publicIp, Scope.COMPANY, true);
    if (companyMatch) return true;

    // Check INDIVIDUAL scope (employee-specific IP)
    boolean individualMatch = validIpRepository
      .existsByIpAddressAndScopeAndEmployeeIdAndIsActive(
        publicIp, Scope.INDIVIDUAL, employeeId, true);
    return individualMatch;
  }
}
```

### 4.3 Check-in Controller: Tích hợp IP Validation

```java
// controller/AttendanceController.java
@PostMapping("/api/attendance/check-in")
public ResponseEntity<AttendanceRecordDto> checkIn(
    @RequestBody CheckInRequest request,
    @AuthenticationPrincipal UserDetails user,
    HttpServletRequest httpRequest) {

  // Resolve public IP from request
  String publicIp = ipAddressResolver.resolve(httpRequest);

  // Validate IP (skip if client-site mode)
  if (!request.isClientSite()) {
    boolean ipValid = ipValidationService.isValidOfficeIp(publicIp, user.getId());
    if (!ipValid) {
      return ResponseEntity.status(403).body(
        new ErrorResponse("INVALID_IP", "Không nhận diện được mạng văn phòng"));
    }
  }

  // Continue with photo upload, GPS, attendance record creation...
  AttendanceRecord record = attendanceService.checkIn(
    publicIp,            // ← previously mac_address
    request.getLatitude(),
    request.getLongitude(),
    photoUrl,
    request.isClientSite(),
    user.getId()
  );

  return ResponseEntity.status(201).body(mapper.toDto(record));
}
```

### 4.4 Database: Bảng `valid_ips` (thay `valid_macs`)

```sql
CREATE TABLE valid_ips (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,

  -- IP Address
  ip_address VARCHAR(45) NOT NULL COMMENT 'IPv4 hoặc IPv6 public IP của văn phòng',

  -- Scope
  scope ENUM('COMPANY', 'INDIVIDUAL') NOT NULL DEFAULT 'COMPANY' COMMENT 'Toàn công ty hoặc nhân viên cụ thể',
  employee_id CHAR(36) COMMENT 'FK → users (null for COMPANY scope)',

  -- Metadata
  description VARCHAR(255) COMMENT 'Ghi chú: e.g., "Văn phòng HCM", "IP dự phòng"',
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

### 4.5 Flyway Migration

```
V006__Rename_MAC_To_IP.sql:
  - Rename table: valid_macs → valid_ips
  - Rename column: mac_address → ip_address
  - Rename column: check_in_mac → check_in_ip (in attendance_records)
  - Rename column: check_out_mac → check_out_ip (in attendance_records)
  - Update column types: VARCHAR(17) → VARCHAR(45) (to support IPv6)
```

---

## 5. Lưu ý Bảo mật (Security Considerations)

### 5.1 Rủi ro: IP Spoofing

**Rủi ro:** Kẻ xấu có thể giả mạo header `X-Forwarded-For`.

**Giảm thiểu:**
- Chỉ tin tưởng `X-Forwarded-For` khi đến từ reverse proxy đã cấu hình (Nginx).
- Cấu hình Nginx: `proxy_set_header X-Forwarded-For $remote_addr;` (ghi đè, không append).
- Trong môi trường production, Nginx là nguồn duy nhất set header này.

### 5.2 Rủi ro: Dynamic IP

**Rủi ro:** ISP có thể thay đổi public IP của văn phòng.

**Giảm thiểu:**
- Admin được thông báo khi IP thay đổi (check-in failures sẽ spike trong monitoring).
- Admin có thể thêm IP mới trong vòng vài phút qua UI.
- Cân nhắc phối hợp với IT để dùng Static IP cho kết nối văn phòng.

### 5.3 So sánh với MAC Validation

| Tiêu chí | MAC Validation | Public IP Validation |
|---------|--------------|-------------------|
| Khó bypass | Cao (phần cứng) | Trung bình (network) |
| Khả thi với Web | ❌ Không thể | ✅ Sẵn sàng |
| Quản lý whitelist | Phức tạp (nhiều thiết bị) | Đơn giản (vài IP văn phòng) |
| Thay đổi tự động | Không (MAC cố định) | Có (cần cập nhật khi ISP thay đổi IP) |
| MVP phù hợp | ❌ Không triển khai được | ✅ Phù hợp |

---

## 6. Hậu quả (Consequences)

### 6.1 Tích cực

- ✅ **Triển khai được ngay:** Không cần native app, không cần API đặc biệt.
- ✅ **Backend hoàn toàn kiểm soát:** IP đọc server-side, client không thể can thiệp.
- ✅ **Quản lý whitelist đơn giản:** Văn phòng thường chỉ có 1-3 public IP.
- ✅ **Tương thích tất cả thiết bị:** Hoạt động trên mọi browser, OS.
- ✅ **Audit trail tốt:** `check_in_ip` ghi lại IP tại thời điểm chấm công.

### 6.2 Tiêu cực

- ❌ **Không phân biệt thiết bị:** Bất kỳ ai kết nối cùng mạng văn phòng đều qua được (nhưng vẫn cần authenticate JWT).
- ❌ **Dynamic IP rủi ro:** Admin phải cập nhật whitelist khi IP thay đổi.
- ❌ **VPN/Remote work:** Nhân viên làm remote qua VPN văn phòng có thể vô tình qua được validation (tùy VPN config).

---

## 7. Phạm vi Thay đổi (Change Scope)

### 7.1 Database

| Thay đổi | Trước | Sau |
|---------|-------|-----|
| Tên bảng | `valid_macs` | `valid_ips` |
| Cột định danh | `mac_address VARCHAR(17)` | `ip_address VARCHAR(45)` |
| Cột check-in | `check_in_mac VARCHAR(17)` | `check_in_ip VARCHAR(45)` |
| Cột check-out | `check_out_mac VARCHAR(17)` | `check_out_ip VARCHAR(45)` |

### 7.2 Backend

| File | Thay đổi |
|------|---------|
| `ValidMac.java` | Rename → `ValidIp.java`, field `macAddress` → `ipAddress` |
| `ValidMacRepository.java` | Rename → `ValidIpRepository.java` |
| `AttendanceService.java` | Param `macAddress` → `publicIp` |
| `CheckInRequest.java` | Remove `macAddress` field (IP resolved server-side) |
| New: `IpAddressResolver.java` | Utility để đọc Public IP từ request |
| New: `IpValidationService.java` | Logic validate IP vs `valid_ips` table |

### 7.3 Frontend

| File | Thay đổi |
|------|---------|
| `MacWifiStatus.tsx` | Rename → `IpStatus.tsx`, hiển thị trạng thái IP validation |
| `useWifiMac.ts` | Rename → `useIpValidation.ts`, gọi `GET /api/attendance/validate-ip` |
| `MacManagerGrid.tsx` | Rename → `IpManagerGrid.tsx` |
| `MacAddForm.tsx` | Rename → `IpAddForm.tsx`, validation regex IPv4/IPv6 |
| `adminService.ts` | Endpoint `/api/admin/valid-macs` → `/api/admin/valid-ips` |

---

## 8. Quyết định liên quan (Related Decisions)

- **ADR-001:** Tech Stack — không ảnh hưởng, vẫn dùng Spring Boot + React.
- **FR-4 (Client Site Mode):** Không đổi — client site mode vẫn bỏ qua IP validation.
- **FR-19 (Admin Management):** Scope thay đổi từ "Quản lý MAC" sang "Quản lý IP".

---

## 9. Tài liệu cần cập nhật sau ADR này

- [x] `docs/architecture/database_schema.md` — đổi `valid_macs` → `valid_ips`, `check_in_mac` → `check_in_ip`
- [x] `docs/ux/` — thay "MAC Wi-Fi" → "Public IP" trong tất cả UX docs
- [x] `_bmad-output/planning-artifacts/epics.md` — cập nhật FR-1, FR-19, Story 3.1
- [ ] `docs/architecture/architecture.md` — cập nhật diagram check-in flow
- [ ] PRD (nếu có file riêng) — cập nhật FR-1, FR-19

---

**Status:** ✅ Accepted  
**Last Updated:** 2026-06-04  
**Architecture Lead:** Claude Code (BMAD)  
**Stakeholder Sign-off:** Pending — requires Product Owner confirmation before Sprint 1 coding begins  
**Review Date:** 2026-07-04
