# UI State Mapping — State Machine Synchronization

**Purpose:** Map PRD State Machine (§5 from prd.md) to React UI states  
**Scope:** How backend `attendance_status` and `approval_sub_status` translate to visual & behavioral states in UI

---

## 1. Attendance Status → Visual States

### 1.1 Status Badge Colors (All Roles)

| PRD Status | Badge Color | Icon | UI Label | Meaning |
|---|---|---|---|---|
| `ON_TIME` | Green (emerald-600) | ✓ | Đúng giờ | Check-in & check-out within thresholds |
| `LATE_IN` | Red (red-600) | ⚠️ | Đi muộn | Check-in after `shift_start + late_threshold` |
| `EARLY_OUT` | Amber (amber-600) | ⚠️ | Về sớm | Check-out before `shift_end - early_threshold` |
| `LATE_IN_EARLY_OUT` | Red (red-600) | ⚠️ | Đi muộn & về sớm | Both violations |
| `HALF_DAY` | Red (red-600) | ❌ | Nửa ngày | Exceeds `half_day_threshold` |
| `INCOMPLETE` | Amber (amber-600) | ? | Chưa hoàn chỉnh | No check-out after grace period |
| `ABSENT` | Gray (slate-600) | ✗ | Vắng mặt | No check-in for scheduled day |

### 1.2 Employee History Screen

When viewing personal attendance history, each card displays:

```
┌─────────────────────────────────────┐
│ 2026-06-04 (Thứ Ba)                │
│                                      │
│ [✓ ON_TIME] (green badge)          │
│                                      │
│ 08:45 — Đúng giờ                   │
│ 17:30 — Check-out                  │
│                                      │
│ ✓ Đủ 3 yếu tố (Public IP, GPS, Ảnh) │
│                                      │
│ [Xem chi tiết]                      │
└─────────────────────────────────────┘
```

If status is violation (LATE_IN, EARLY_OUT, HALF_DAY):

```
┌─────────────────────────────────────┐
│ 2026-06-04                          │
│                                      │
│ [⚠️ LATE_IN]  (red badge)          │
│                                      │
│ 09:20 — Đi muộn 20 phút            │
│ 17:30 — Check-out                  │
│                                      │
│ [Gửi Yêu cầu Ngoại lệ]             │
│ [Xem chi tiết]                      │
└─────────────────────────────────────┘
```

### 1.3 Leader Roster Card

```
┌──────────────────────────────────────────┐
│ Minh                   [✓ ON_TIME]       │
│ Ca Sáng: 9:00-17:30                      │
│ 08:45 — 17:30                           │
│                                          │
│ [Xem chi tiết]                           │
└──────────────────────────────────────────┘

OR (with violation + pending request)

┌──────────────────────────────────────────┐
│ Lan                    [⚠️ LATE_IN]      │
│ Ca Sáng: 9:00-17:30                      │
│ 09:20 — 17:30                           │
│                                          │
│ [🔴 PENDING] Yêu cầu Ngoại lệ          │
│ [Duyệt] [Từ chối]                       │
│                                          │
│ [Xem chi tiết]                           │
└──────────────────────────────────────────┘
```

---

## 2. Approval Sub-status → UI Behavior

### 2.1 Sub-status Overlay States

| Sub-status | Trigger | Visual Effect | Available Actions |
|---|---|---|---|
| `NULL` (no request) | Initial state after check-out | Status badge only | Send request (if violation) |
| `PENDING_APPROVAL` | Employee submits Exception/Adjustment Request | Status badge + [PENDING] badge (amber) | Wait for Leader review |
| `APPROVED` | Leader/Admin approves request | Status badge + [✓ APPROVED] badge (green) | View approval details |
| `REJECTED` | Leader/Admin rejects request | Original status remains + [✗ REJECTED] badge (red) | Can resubmit request |
| `ADMIN_OVERRIDE` | Admin directly edits record | [⚙️ OVERRIDE] badge (blue) | View audit log |

### 2.2 Employee Perspective

**Scenario 1: Normal Check-in (ON_TIME)**
```
Initial (after check-in):           After check-out:
┌───────────────────────┐          ┌───────────────────────┐
│ Status: PENDING       │          │ Status: ON_TIME       │
│ (waiting for check-out) ────────► │ Sub-status: NULL      │
└───────────────────────┘          │ Can view history      │
                                   └───────────────────────┘
```

**Scenario 2: Violation + Request**
```
After check-out:              Employee sends request:     Leader approves:
┌───────────────────┐        ┌──────────────────────┐    ┌──────────────────┐
│ Status: LATE_IN   │        │ Status: LATE_IN      │    │ Status: LATE_IN  │
│ Sub-status: NULL  │ ─────► │ Sub-status:PENDING   │ ─► │ Sub-status:APPROV│
│ [Send request]    │        │ [Waiting...]         │    │ ✓ Approved       │
└───────────────────┘        └──────────────────────┘    └──────────────────┘
```

**Scenario 3: Request Rejected**
```
After rejection:
┌──────────────────────────┐
│ Status: LATE_IN          │
│ Sub-status: REJECTED     │
│ ✗ Từ chối                │
│                          │
│ Reason: [Admin's reason] │
│                          │
│ [Gửi lại]               │
└──────────────────────────┘
```

### 2.3 Leader Perspective

**In Pending Requests Tab:**
```
Card shows:
- Employee name
- Date + time
- Original status (LATE_IN, EARLY_OUT, HALF_DAY)
- Sub-status: PENDING_APPROVAL
- Employee's reason (text)
- Attendance record preview
- [Duyệt] [Từ chối] buttons (enabled)

After approval:
- Sub-status: APPROVED
- [Duyệt] [Từ chối] buttons disabled
- Show: "Đã duyệt vào [timestamp]"
```

**In Daily Roster:**
```
If employee has PENDING request on a violation:
┌─────────────────────────────────────┐
│ Minh            [⚠️ LATE_IN]        │
│ 09:20 — 17:30                       │
│                                     │
│ [🔴 PENDING] Yêu cầu Ngoại lệ      │
│ [Duyệt] [Từ chối]                  │
│                                     │
│ Count of pending requests in badge: │
│ "Requests (2)" ← unread count       │
└─────────────────────────────────────┘
```

---

## 3. Check-in/Check-out Flow State Diagram

### 3.1 Employee Check-in Screen

```
┌─────────────────────────────────────────────────────────────────┐
│ CheckInScreen State Machine (React)                              │
└─────────────────────────────────────────────────────────────────┘

[INITIAL]
  ↓
[CHECKING_IP] ──► {ipValid: true}  → [READY_FOR_PHOTO]
              ──► {ipValid: false} → [SHOW_CLIENT_SITE_TOGGLE]
  ↓
[GATHERING_GPS] (async in background)
  ↓
[SHOW_CAMERA]
  ├─ Photo not captured → Button DISABLED
  ├─ Photo captured     → Button ENABLED
  ↓
[PHOTO_CAPTURED]
  ├─ User clicks [Xác nhận Check-in]
  ├─ isSubmitting: true → Button shows spinner
  ↓
[SUBMITTING_TO_BACKEND]
  ├─ Success → navigate to success screen
  └─ Error   → Show error toast, enable retry

Success Response from Backend:
{
  id: "uuid",
  status: "ON_TIME" | "LATE_IN" | "INCOMPLETE" | etc.,
  subStatus: null,
  checkInTime: "2026-06-04T08:45:00Z",
  checkOutTime: null,
  checkInIp: "203.0.113.45",  // Public IP recorded at check-in
  gps: { lat, lng, accuracy },
  photoUrl: "https://s3.../photo.jpg"
}

UI Response:
┌──────────────────────────────────────┐
│     ✓ Check-in Thành công!           │
│                                       │
│     08:45 — Đúng giờ                │
│                                       │
│ Bản ghi được lưu với đủ 3 yếu tố:  │
│ ✓ Public IP                          │
│ ✓ GPS                                │
│ ✓ Ảnh                                │
│                                       │
│        [Quay về trang chủ]          │
└──────────────────────────────────────┘
```

### 3.2 Exception Request Flow

```
[EMPLOYEE VIEWS VIOLATION RECORD]
  {status: LATE_IN, subStatus: null}
  ↓
[CLICK "Gửi Yêu cầu Ngoại lệ"]
  ↓
[SHOW FORM]
  - Type: LATE_IN (read-only)
  - Reason: (text input, required)
  ↓
[SUBMIT]
  - POST /api/requests/exception
  - {attendanceRecordId, requestType, reason}
  ↓
[BACKEND RESPONSE SUCCESS]
  - attendance_record.subStatus = PENDING_APPROVAL
  - exception_request created with status = PENDING
  ↓
[UI STATE UPDATE]
  - Show: "✓ Yêu cầu đã gửi"
  - Record now shows: [🟠 PENDING] badge
  - Button "Gửi yêu cầu" becomes disabled

[NOTIFICATION TO LEADER]
  - In-app notification: "Minh xin phép đi muộn"
  - Email: Same message
  - Badge count increments
  ↓
[LEADER VIEWS REQUEST]
  - Card shows: Employee + reason + attendance preview
  - [Duyệt] or [Từ chối] buttons
  ↓
[LEADER CLICKS DUYỆT]
  - PUT /api/requests/{id}/approve
  ↓
[BACKEND UPDATES]
  - exception_request.status = APPROVED
  - attendance_record.subStatus = APPROVED
  ↓
[UI STATE UPDATES FOR BOTH]
  - Leader: See [✓ APPROVED] badge, request moves to "Approved" tab
  - Employee: History card now shows [✓ APPROVED], vi phạm không tính
```

---

## 4. Modal & Form State Management

### 4.1 RequestDetailModal (Leader Approval)

```typescript
interface RequestDetailModalState {
  isOpen: boolean;
  request: ExceptionRequest | null;
  isApproving: boolean;
  isRejecting: boolean;
  showRejectReason: boolean;
  rejectReasonText: string;
  error?: string;
}

// State transitions:
// CLOSED → OPEN (user clicks request card)
// OPEN, isApproving=false, isRejecting=false → Button clicks enabled
// Button clicked → isApproving=true OR showRejectReason=true
// API response success → CLOSED, parent state updated
// API response error → error message shown, can retry
```

### 4.2 ShiftEditRow (Admin Config)

```typescript
interface ShiftEditRowState {
  isEditing: boolean;
  originalShift: Shift;
  formData: Shift (mutable copy)
  auditReason: string
  isSubmitting: boolean
  error?: string
}

// Triggers:
// Click row → isEditing=true
// Change input → formData updated
// Type audit reason → auditReason updated
// Click Save → Validate audit reason (required, not empty)
//           → isSubmitting=true
//           → POST audit log + update record
//           → isSubmitting=false, isEditing=false
// Click Cancel → Reset to originalShift, isEditing=false
```

---

## 5. Real-time Polling & Notification Badge

### 5.1 Notification Badge Count (Leader/Admin)

```typescript
// hooks/useNotificationCount.ts
export const useNotificationCount = () => {
  // Polling every 15 seconds
  return useQuery({
    queryKey: ['notification-count'],
    queryFn: async () => {
      const response = await api.get('/api/notifications/count');
      return response.data.unreadCount; // number
    },
    refetchInterval: 15 * 1000,
    refetchOnWindowFocus: true,
  });
};

// In Header:
<NotificationBell unreadCount={unreadCount} />

// UI updates automatically when query refetches
// Badge shows red dot if count > 0
// Shows number if count > 0
```

### 5.2 Leader Daily Roster Auto-refresh

```typescript
// hooks/useLeaderRoster.ts
export const useLeaderRoster = (date: Date) => {
  return useQuery({
    queryKey: ['leader-roster', date.toISOString()],
    queryFn: () => leaderService.getDailyRoster(date),
    refetchInterval: 15 * 1000, // 15 seconds
    refetchOnWindowFocus: true,
  });
};

// When a new exception request is submitted by employee:
// 1. Backend creates exception_request + updates attendance_record.subStatus
// 2. Roster query refetches
// 3. New PENDING badge appears on that employee's card
// 4. Notification badge count increments
```

---

## 6. Error States & Validation

### 6.1 Check-in Form Validation

```typescript
// Note: macAddress removed — Public IP resolved server-side (ADR-002)
const CheckInFormSchema = z.object({
  latitude: z.number().min(-90).max(90).optional(),
  longitude: z.number().min(-180).max(180).optional(),
  photoBase64: z
    .string()
    .min(1000, 'Ảnh quá nhỏ')
    .refine(
      (photo) => photo.length <= 600000,
      'Ảnh quá lớn (max 500KB)'
    ),
  isClientSite: z.boolean(),
});

// Before submit, form validation runs
// If validation fails: focus on first error field, show inline error message
// If IP invalid AND isClientSite=false: Show error "Cần bật Client Site Mode"
// If isClientSite=true AND no GPS: Show error "GPS bắt buộc khi chấm công ngoài"
```

### 6.2 API Error Handling

```typescript
// Example: Check-in fails due to IP not in whitelist
const handleSubmit = async () => {
  try {
    await checkIn(data);
  } catch (err) {
    if (err.response?.status === 403) {
      if (err.response.data.code === 'INVALID_IP') {
        setError('Không nhận diện được mạng văn phòng');
        showClientSiteToggle = true;
      } else if (err.response.data.code === 'GPS_REQUIRED') {
        setError('GPS bắt buộc. Vui lòng bật định vị.');
      } else {
        setError(err.response.data.message);
      }
    } else if (err.response?.status === 409) {
      // Duplicate check-in same minute
      setError('Bạn đã chấm công rồi. Hãy chấm công lúc hết ca.');
    } else {
      setError('Lỗi không xác định. Vui lòng thử lại.');
    }
  }
};
```

---

## 7. Accessibility & State Announcement

### 7.1 ARIA Live Regions

For dynamic state changes that don't require navigation:

```tsx
// Toast notification (auto-dismiss, screen reader friendly)
<div
  role="status"
  aria-live="polite"
  aria-atomic="true"
  className="toast"
>
  ✓ Check-in thành công
</div>

// Form validation error
<div
  role="alert"
  aria-live="assertive"
  aria-atomic="true"
>
  ⚠️ Ảnh là bắt buộc
</div>

// Notification badge update
<div
  role="status"
  aria-live="polite"
  aria-label={`Có ${unreadCount} thông báo mới`}
>
  🔔 {unreadCount}
</div>
```

### 7.2 Keyboard State Management

```typescript
// RequestDetailModal: Close on Escape key
useEffect(() => {
  const handleEscape = (e: KeyboardEvent) => {
    if (e.key === 'Escape') onClose();
  };
  document.addEventListener('keydown', handleEscape);
  return () => document.removeEventListener('keydown', handleEscape);
}, [onClose]);

// Tab navigation within modal: Focus trap
// First interactive element: request details
// Last interactive element: Cancel button
// Tab from Cancel → Focus returns to first element
```

---

## 8. Summary: State Flow Example

**Complete journey from check-in to approval:**

```
┌─ EMPLOYEE CHECK-IN ──────────────────────────────────────────┐
│                                                                │
│ 1. Open CheckInScreen                                        │
│    - MAC status: checking...                                 │
│    - GPS status: requesting permission                       │
│    - Camera: requesting permission                           │
│                                                              │
│ 2. Collect data                                              │
│    - MAC valid ✓                                             │
│    - GPS received: lat/lng/accuracy                          │
│    - Photo captured (base64)                                 │
│    - Button enabled                                          │
│                                                              │
│ 3. Submit                                                    │
│    - Backend validates: MAC ✓, GPS ✓, Photo ✓              │
│    - Calculates attendance_status based on time             │
│      → check_in_time = 09:20                                │
│      → shift_start = 09:00                                  │
│      → late_threshold = 0                                   │
│      → status = LATE_IN (09:20 > 09:00 + 0)               │
│    - Creates AttendanceRecord with status=LATE_IN           │
│    - Response includes record with subStatus=NULL           │
│                                                              │
└──────────────────────────────────────────────────────────────┘

┌─ EMPLOYEE HISTORY ───────────────────────────────────────────┐
│                                                                │
│ 1. View personal history                                     │
│    - Card shows: 2026-06-04 [⚠️ LATE_IN]                   │
│    - 09:20 — Đi muộn 20 phút                               │
│    - Sub-status shown: PENDING (initially NULL, no action)  │
│    - "Gửi Yêu cầu Ngoại lệ" button visible                │
│                                                              │
│ 2. Click "Gửi Yêu cầu Ngoại lệ"                            │
│    - Form appears: Type=LATE_IN, Reason=[input]            │
│    - Submit → POST /api/requests/exception                  │
│                                                              │
│ 3. Backend updates                                           │
│    - exception_request created, status=PENDING              │
│    - attendance_record.subStatus = PENDING_APPROVAL         │
│    - Notification sent to Leader                            │
│    - Response includes updated record                       │
│                                                              │
│ 4. UI updates                                                │
│    - Card now shows [🟠 PENDING] badge                     │
│    - "Gửi Yêu cầu" button disabled                         │
│    - Toast: "✓ Yêu cầu đã gửi"                             │
│                                                              │
└──────────────────────────────────────────────────────────────┘

┌─ LEADER APPROVAL ────────────────────────────────────────────┐
│                                                                │
│ 1. Leader receives notification                              │
│    - In-app notification: badge count +1                    │
│    - Email: "Minh xin phép đi muộn ngày 2026-06-04"        │
│                                                              │
│ 2. Leader clicks notification → RequestDetailModal          │
│    - Shows: Employee, reason, attendance record preview    │
│    - [Duyệt] [Từ chối] buttons enabled                     │
│                                                              │
│ 3. Leader clicks "Duyệt"                                    │
│    - PUT /api/requests/{id}/approve                         │
│    - Backend: exception_request.status = APPROVED           │
│    - Backend: attendance_record.subStatus = APPROVED        │
│                                                              │
│ 4. Both Leader & Employee see update                         │
│    - Leader: Request card moves to "Approved" tab           │
│    - Leader: Notification badge count decrements            │
│    - Employee: History card shows [✓ APPROVED]             │
│    - Employee: Receives in-app notification                │
│    - Employee: Vi phạm không tính trong báo cáo             │
│                                                              │
└──────────────────────────────────────────────────────────────┘

Final state in database:
{
  id: "uuid",
  employee_id: "minh-id",
  date: "2026-06-04",
  check_in_time: "2026-06-04T09:20:00Z",
  check_out_time: "2026-06-04T17:30:00Z",
  attendance_status: "LATE_IN",  ← Main status (from State Machine)
  approval_sub_status: "APPROVED",  ← Overlay status
  is_admin_override: false,
  checkInIp: "203.0.113.45",  // Public IP recorded at check-in
  gps: {lat, lng, accuracy},
  photos: ["s3://...check-in.jpg", "s3://...check-out.jpg"],
  created_at: "2026-06-04T09:20:30Z",
  updated_at: "2026-06-04T17:35:00Z" (when request approved)
}
```

---

## 9. Testing Checklist

| Scenario | Expected UI State | Validation |
|---|---|---|
| Check-in on time | ON_TIME badge (green) | Button disabled before photo |
| Check-in late | LATE_IN badge (red) | Exception request button enabled |
| Send request | Sub-status: PENDING | Badge shown, button disabled |
| Leader approves | Sub-status: APPROVED | Notification cleared, badge updated |
| Leader rejects | Sub-status: REJECTED | Option to resubmit |
| Admin overrides record | IS_ADMIN_OVERRIDE flag | Audit log visible |
| GPS unavailable | Flag set, but check-in allowed | Toast warning shown |
| IP invalid (INVALID_IP) | Show Client Site toggle | GPS becomes required |
| Photo upload fails | Error message, retry button | Check-in not created |
| Network offline | Queue request, sync later | Offline banner shown |

---

## 10. Design System Reference

See `01-ux-overview.md` for:
- Color palette (emerald, red, amber, slate)
- Typography scale
- Spacing units (4px base)
- Responsive breakpoints
