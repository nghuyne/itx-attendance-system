# ADR-003: Hybrid MAC + IP Validation — BSSID cho Mobile, IP cho Web

**Status:** Accepted  
**Date:** 2026-06-26  
**Supersedes:** ADR-002 (một phần — IP validation vẫn giữ nguyên cho web path)  
**Context:** ISP cấp dynamic IP gây bất tiện cho employee check-in; Capacitor Android app mở ra khả năng đọc BSSID qua native API  
**Audience:** Backend/Frontend engineers, Mobile engineers, architects

---

## 1. Bối cảnh (Context)

### 1.1 Vấn đề Phát hiện

Sau khi ADR-002 (2026-06-04) được triển khai, nhóm kỹ thuật phát hiện một hạn chế thực tế quan trọng:

> **ISP thường xuyên cấp phát Dynamic IP — router văn phòng đổi Public IP mỗi lần restart, khiến admin phải cập nhật whitelist thủ công.**

**Nguyên nhân gốc rễ của ADR-002:** ADR-002 chọn Public IP validation vì Web Browser API (Chrome, Firefox, Safari) không cho phép đọc MAC address hay BSSID của Wi-Fi adapter. Do đó, network identifier duy nhất khả thi trên web là Public IP của router.

Hạn chế mới phát sinh sau khi ADR-002 được áp dụng:
- Nhiều ISP tại Việt Nam không cung cấp Static IP cho gói văn phòng tiêu chuẩn.
- Mỗi khi router bị reset (mất điện, bảo trì), toàn bộ employee check-in Office Mode thất bại cho đến khi admin cập nhật IP mới.
- Điều này tạo ra bottleneck phụ thuộc vào phản ứng của admin — không phù hợp môi trường sản xuất.

### 1.2 Điều kiện Kỹ thuật Mới

Team quyết định xây dựng Android app dùng **Capacitor** (cross-platform framework từ Ionic). Điều này mở ra khả năng:

- Plugin `@capacitor-community/wifi-info` cho phép đọc **BSSID** (Basic Service Set Identifier) qua OS-level native API trên Android (yêu cầu `ACCESS_FINE_LOCATION` permission, Android 6.0+ / API 23+).
- **BSSID = MAC address của Wi-Fi router** — là định danh phần cứng cố định, không thay đổi dù IP thay đổi.
- iOS cũng có API tương tự nhưng bị Apple hạn chế từ iOS 13+ (yêu cầu special entitlement) → không khả thi trong ngắn hạn.

### 1.3 Phân tích Đối tượng Người dùng

| Nhóm | Thiết bị | Hành động chính | Vấn đề với IP động |
|------|---------|----------------|-------------------|
| **Employee** | Android app (Capacitor) | Check-in Office Mode hàng ngày | Bị ảnh hưởng trực tiếp → giải quyết bởi ADR này |
| **Admin** | Web browser | Quản lý hệ thống, xem report | Ít check-in hơn; IP validation đủ dùng |
| **Leader** | Web browser | Duyệt request, xem attendance | Ít check-in hơn; IP validation đủ dùng |
| **iOS Employee** | iOS/Web browser | Check-in Office Mode | Vẫn dùng IP path — vấn đề dynamic IP còn tồn tại (xem Mục 6.2) |

---

## 2. Quyết định (Decision)

**Áp dụng Hybrid Validation: BSSID cho mobile Android employee check-in, IP tiếp tục cho web (admin/leader).**

### 2.1 Logic Phân nhánh (Backend)

```
if (isClientSiteMode)            → bỏ qua network check, chỉ GPS + photo    (Remote/Client-site — không đổi)
else if (bssid != null && valid) → validate BSSID vs bảng valid_macs          (Android mobile path — MỚI)
else                             → validate IP vs bảng valid_ips              (Web browser path — giữ nguyên)
```

**Backend phát hiện client type qua field `bssid` trong request body:**
- `bssid` có giá trị (non-null, non-blank, valid format `XX:XX:XX:XX:XX:XX`) → Android app → dùng BSSID validation
- `bssid` là null, blank, hoặc không có → Web browser → dùng IP validation

### 2.2 Phạm vi Thay đổi

- **`valid_ips` table**: Giữ nguyên — tiếp tục phục vụ web path.
- **`valid_macs` table**: Thêm mới — lưu BSSID whitelist của router/AP văn phòng.
- **`valid_macs` không có scope hay employee FK**: BSSID là định danh duy nhất của router toàn công ty — không cần phân cấp COMPANY/INDIVIDUAL như `valid_ips`.
- **Remote/Client-site mode**: Không thay đổi — bỏ qua cả BSSID lẫn IP, chỉ dùng GPS + photo.
- **Multi-AP office**: Mỗi Access Point trong văn phòng có BSSID riêng — admin cần đăng ký BSSID của từng AP, đặc biệt trong môi trường mesh network.

---

## 3. Phân tích Lựa chọn (Alternatives Considered)

| Phương án | Ưu điểm | Nhược điểm | Kết quả |
|-----------|---------|-----------|---------|
| **Full MAC only** (bắt buộc tất cả dùng app) | Không bị ảnh hưởng IP động; Chính xác hơn | iOS phức tạp (special entitlement); Admin/Leader phải cài app; Tăng scope | Loại |
| **Full IP (giữ ADR-002)** | Không cần thay đổi gì; Đơn giản | Vẫn có vấn đề dynamic IP cho employee; Admin phải cập nhật thủ công thường xuyên | Loại |
| **Hybrid MAC + IP** ✅ | Đúng đối tượng (employee dùng app, web users không đổi); BSSID cố định → ít cần cập nhật; Không phá vỡ web path hiện tại | Cần maintain 2 validation path; Employee phải cài Android app; iOS không được hưởng lợi | **Chọn** |

---

## 4. Chi tiết Triển khai (Implementation Details)

### 4.1 Schema Mới: Bảng `valid_macs`

```sql
CREATE TABLE valid_macs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    bssid       VARCHAR(17)  NOT NULL,           -- format: XX:XX:XX:XX:XX:XX (uppercase)
    description VARCHAR(255),
    created_by  VARCHAR(36)  NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NULL ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by  VARCHAR(36)  NULL,
    UNIQUE KEY uq_valid_macs_bssid (bssid),
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (updated_by) REFERENCES users(id)
)
```

**Lưu ý thiết kế:**
- `bssid` lưu uppercase — normalize trước khi insert và lookup.
- `updated_at` / `updated_by` cho audit trail khi toggle `is_active`.
- Không có `scope` hay `employee_id` — BSSID áp dụng toàn công ty.
- `bssid` unique constraint — mỗi router/AP chỉ đăng ký một lần.

### 4.2 Backend: Logic Phát hiện và Phân nhánh

```java
// Pseudocode trong AttendanceService.checkIn()
public AttendanceRecordDto checkIn(CheckInRequest request, String userId, String clientIp) {

    if (!Boolean.TRUE.equals(request.getIsClientSiteMode())) {
        String bssid = request.getBssid();
        if (bssid != null && !bssid.isBlank()) {
            // Android mobile path — validate format trước
            String normalizedBssid = bssid.toUpperCase();
            if (!normalizedBssid.matches("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")) {
                throw new BusinessException("INVALID_BSSID_FORMAT", HttpStatus.BAD_REQUEST);
            }
            // "02:00:00:00:00:00" là redacted BSSID khi location permission bị từ chối
            if ("02:00:00:00:00:00".equals(normalizedBssid)) {
                throw new BusinessException("INVALID_BSSID", HttpStatus.FORBIDDEN);
            }
            boolean bssidValid = validMacRepository
                .existsByBssidAndIsActive(normalizedBssid, true);
            if (!bssidValid) throw new BusinessException("INVALID_BSSID", HttpStatus.FORBIDDEN);

        } else {
            // Web browser path (ADR-002 logic — không đổi)
            boolean ipValid = ipValidationService.isValidOfficeIp(clientIp, userId);
            if (!ipValid) throw new BusinessException("INVALID_IP", HttpStatus.FORBIDDEN);
        }
    }

    // Tiếp tục: GPS validation, photo upload, tạo attendance record...
}
```

### 4.3 CheckIn Request DTO: Thêm Field `bssid`

```java
public record CheckInRequestDto(
    Double latitude,
    Double longitude,
    String photoBase64,
    Boolean isClientSiteMode,
    String bssid           // null hoặc blank = web browser; non-null/non-blank = Android app
) {}
```

### 4.4 Admin UI: Quản lý BSSID

- `GET /api/admin/valid-macs` — danh sách BSSID whitelist.
- `POST /api/admin/valid-macs` — thêm mới; validate format regex `^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$`; normalize uppercase trước khi lưu.
- `PATCH /api/admin/valid-macs/{id}` — toggle `is_active` (deactivate router/AP cũ); ghi `updated_by`.
- `DELETE /api/admin/valid-macs/{id}` — xóa vĩnh viễn; yêu cầu confirmation UI.
- Hiển thị `description` để admin ghi chú (ví dụ: "Router tầng 2 văn phòng HCM", "AP mesh node B").

### 4.5 Android Frontend: Đọc BSSID

```typescript
// Capacitor plugin: @capacitor-community/wifi-info v3.x
// Yêu cầu: Android 6.0+ (API 23+), ACCESS_FINE_LOCATION permission
// Android 12+ (API 31+): plugin dùng WifiManager.getCurrentNetwork() API
import { WifiInfo } from '@capacitor-community/wifi-info';

async function getBssid(): Promise<string | null> {
  try {
    const { bssid } = await WifiInfo.getWifiInfo();
    // "02:00:00:00:00:00" = redacted value khi location permission bị từ chối
    if (!bssid || bssid === '02:00:00:00:00:00') return null;
    return bssid.toUpperCase();
  } catch {
    // Fallback về IP path — xem Mục 5.3 về hậu quả của fallback này
    return null;
  }
}
```

---

## 5. Lưu ý Bảo mật (Security Notes)

### 5.1 Rủi ro: Client-Declared Validation Path

**Rủi ro:** Backend chọn BSSID hay IP path dựa trên field `bssid` do client gửi lên. Bất kỳ HTTP client nào (curl, web browser) đều có thể tự khai là Android app bằng cách gửi BSSID tùy ý.

**Giảm thiểu:**
- BSSID phải khớp với whitelist — kẻ tấn công cần biết chính xác BSSID hợp lệ của văn phòng.
- GPS cross-check bổ sung: employee vẫn cần ở trong bán kính GPS của văn phòng.
- JWT authentication đảm bảo chỉ user đã xác thực mới gửi được request.

### 5.2 Rủi ro: BSSID Spoofing

**Rủi ro:** Employee biết BSSID hợp lệ có thể gửi giá trị đó từ xa qua HTTP client mà không thực sự ở văn phòng.

**Giảm thiểu:**
- GPS validation là lớp bảo mật bổ sung (employee vẫn cần pass GPS radius check).
- BSSID không public; kẻ tấn công cần scan mạng Wi-Fi thực tế hoặc có quyền truy cập nội bộ.
- Trade-off chấp nhận cho MVP: kết hợp BSSID + GPS + JWT đủ cho use case attendance tracking.

### 5.3 Rủi ro: Location Permission Denied → Fallback Behavior

**Rủi ro:** Android 6.0+ yêu cầu `ACCESS_FINE_LOCATION` để đọc BSSID. Nếu nhân viên từ chối permission, `getBssid()` trả `null` → backend fallback về IP path (dynamic IP problem tồn tại trở lại).

**Giảm thiểu:**
- App hiển thị cảnh báo rõ khi location permission bị từ chối, giải thích check-in sẽ dùng IP fallback.
- `02:00:00:00:00:00` (BSSID redacted) được detect và xử lý tường minh — không phải silent failure.
- Hành vi fallback là deliberate và documented, không phải ẩn trong exception handler.

---

## 6. Hậu quả (Consequences)

### 6.1 Tích cực

- ✅ **Employee không bị ảnh hưởng bởi IP động**: BSSID router cố định → check-in ổn định kể cả khi IP thay đổi.
- ✅ **Admin/Leader không cần thay đổi gì**: IP validation web path giữ nguyên — zero migration effort.
- ✅ **BSSID ít cần cập nhật**: Router thay đổi ít hơn nhiều so với IP; chỉ cần update khi thay/thêm router hoặc AP.
- ✅ **Backward compatible**: Web path hoạt động y như cũ — không ảnh hưởng code hiện tại.

### 6.2 Tiêu cực

- ❌ **Employee phải cài Android app**: Không thể dùng web browser để check-in Office Mode với BSSID validation.
- ❌ **iOS không được hưởng lợi**: iOS employee vẫn dùng IP validation — dynamic IP problem tồn tại. Interim: iOS users cần sử dụng mạng văn phòng với static IP hoặc liên hệ IT. Revisit khi iOS entitlement khả thi.
- ❌ **Maintain 2 validation path**: Backend xử lý cả BSSID và IP path — tăng complexity nhưng trong tầm kiểm soát.
- ❌ **Multi-AP environment**: Admin phải đăng ký BSSID của từng AP riêng biệt; mesh network cần cập nhật whitelist khi thêm node.
- ❌ **Fallback-to-IP khi không đọc BSSID**: Nếu location permission bị từ chối, app fallback về IP path — employee trên Android có thể lách BSSID validation bằng cách từ chối permission trên thiết bị.

### 6.3 Neutral

- **BSSID = định danh toàn công ty**: Không cần phân cấp scope như IP — đơn giản hơn về quản lý dữ liệu.
- **`valid_ips` giữ nguyên**: Không cần migration data hay thay đổi code hiện tại cho IP validation.

---

## 7. Phạm vi Thay đổi (Scope of Changes)

### 7.1 Database

| Thay đổi | Chi tiết |
|---------|---------|
| Bảng mới | `valid_macs` (id, bssid, description, created_by, is_active, created_at, updated_at, updated_by) |
| Bảng giữ nguyên | `valid_ips` — không thay đổi |
| Bảng giữ nguyên | `attendance_records` — không thay đổi cấu trúc |

### 7.2 Backend

| File | Thay đổi |
|------|---------|
| New: `ValidMac.java` | Entity cho bảng `valid_macs` |
| New: `ValidMacRepository.java` | `existsByBssidAndIsActive()` |
| New: `ValidMacService.java` | CRUD + validation logic |
| Modified: `CheckInRequest.java` | Thêm field `bssid` (nullable String) |
| Modified: `AttendanceService.java` | Thêm BSSID branch trong `checkIn()` |
| Modified: `AdminController.java` | Thêm endpoints `/api/admin/valid-macs` |

### 7.3 Frontend (Android/Capacitor)

| File | Thay đổi |
|------|---------|
| New: `wifiService.ts` | `getBssid()` dùng `@capacitor-community/wifi-info` |
| Modified: `CheckInPage.tsx` | Gọi `getBssid()` và gửi trong request body |
| New: `capacitor.config.ts` | Cấu hình Capacitor app |
| Modified: `AndroidManifest.xml` | Thêm `ACCESS_FINE_LOCATION` permission |

### 7.4 Rollback Plan

Nếu BSSID validation gây widespread check-in failures (plugin update thay đổi API, router firmware đổi BSSID):

1. **Ngắn hạn**: Set `is_active = false` cho tất cả entries trong `valid_macs` → toàn bộ request rơi về IP path tức thì.
2. **Trung hạn**: Feature flag tại `CheckInRequest.bssid` — backend ignore `bssid` field, force IP path cho tất cả.
3. **Revert về ADR-002**: Remove BSSID branch khỏi `AttendanceService.checkIn()`; `valid_macs` table có thể giữ nguyên (không cần rollback migration).

---

## 8. Quyết định liên quan (Related Decisions)

- **ADR-001:** Tech Stack — không ảnh hưởng; vẫn Spring Boot + React.
- **ADR-002:** Superseded một phần — IP validation tiếp tục cho web path; BSSID validation bổ sung cho mobile path.
- **FR-4 (Client Site Mode):** Không đổi — remote/client-site mode vẫn bỏ qua cả BSSID và IP.
- **FR-1 (MAC Wi-Fi validation):** Phục hồi một phần dưới dạng BSSID validation cho Android app.
- **FR-19 (Admin MAC Management):** Scope mở rộng — admin quản lý cả `valid_ips` và `valid_macs`.

---

## 9. Tài liệu cần cập nhật

- [x] `docs/architecture/003-adr-hybrid-mac-ip-validation.md` — file này (ADR-003)
- [ ] `docs/architecture/database_schema.md` — thêm bảng `valid_macs` với đầy đủ columns
- [ ] `_bmad-output/planning-artifacts/epics.md` — cập nhật FR-19 scope (admin quản lý cả MAC và IP)
- [ ] `_bmad-output/project-context.md` — thêm constraint `valid_macs.id → BIGINT/number`

---

**Status:** ✅ Accepted  
**Last Updated:** 2026-06-29  
**Architecture Lead:** Huy (ITX Tech Lead)  
**Review Date:** 2026-07-26
