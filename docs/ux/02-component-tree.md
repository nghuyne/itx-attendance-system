# Component Tree — React Architecture

**Tech Stack:** React 18 + TypeScript + Tailwind CSS v3 + Vite  
**Build Tool:** Vite (no CRA)  
**HTTP Client:** Axios + TanStack Query v5

---

## 1. Project Structure

```
frontend/src/
├── components/
│   ├── common/
│   │   ├── Header.tsx
│   │   ├── Sidebar.tsx
│   │   ├── BottomTabNav.tsx
│   │   ├── NotificationBell.tsx
│   │   ├── Toast.tsx
│   │   └── ErrorBoundary.tsx
│   ├── employee/
│   │   ├── CheckInScreen.tsx
│   │   ├── CameraViewfinder.tsx
│   │   ├── ClientSiteModeToggle.tsx
│   │   ├── IpStatus.tsx
│   │   ├── GpsStatus.tsx
│   │   ├── AttendanceHistoryScreen.tsx
│   │   ├── ExceptionRequestForm.tsx
│   │   └── AdjustmentRequestForm.tsx
│   ├── leader/
│   │   ├── DailyRosterScreen.tsx
│   │   ├── RosterCard.tsx
│   │   ├── PendingRequestsScreen.tsx
│   │   ├── RequestDetailModal.tsx
│   │   ├── ApprovalForm.tsx
│   │   └── RejectionReasonModal.tsx
│   └── admin/
│       ├── AdminDashboard.tsx
│       ├── ShiftConfigForm.tsx
│       ├── IpManagerGrid.tsx
│       ├── HolidayManagerGrid.tsx
│       ├── AttendanceOverrideForm.tsx
│       └── AuditLogViewer.tsx
├── hooks/
│   ├── useAuth.ts
│   ├── useAttendance.ts
│   ├── useNotification.ts
│   ├── useCamera.ts
│   ├── useGeolocation.ts
│   ├── useIpValidation.ts
│   └── useMutation.ts (custom TanStack Query wrapper)
├── services/
│   ├── api.ts (Axios instance + interceptors)
│   ├── attendanceService.ts
│   ├── requestService.ts
│   ├── adminService.ts
│   └── authService.ts
├── store/
│   ├── authStore.ts (Zustand or Context)
│   ├── userStore.ts
│   └── uiStore.ts
├── types/
│   ├── api.ts (DTOs matching backend)
│   ├── domain.ts (Domain entities: AttendanceRecord, etc.)
│   ├── state.ts (UI state enums)
│   └── form.ts (Form-specific types)
├── utils/
│   ├── formatters.ts (formatTime, formatDate)
│   ├── validators.ts (form validators)
│   ├── geoDistance.ts (Haversine formula)
│   └── errorHandler.ts
├── pages/
│   ├── Employee/
│   │   ├── CheckInPage.tsx
│   │   ├── HistoryPage.tsx
│   │   ├── RequestsPage.tsx
│   │   └── SettingsPage.tsx
│   ├── Leader/
│   │   ├── DashboardPage.tsx
│   │   ├── RequestsPage.tsx
│   │   └── ReportsPage.tsx  // TODO: v1.1 stub (Out of MVP scope)
│   └── Admin/
│       ├── ConfigPage.tsx
│       ├── AuditPage.tsx
│       └── AnalyticsPage.tsx  // TODO: v1.1 stub (Out of MVP scope)
├── App.tsx (Main router setup)
├── main.tsx (Entry point)
└── index.css (Global styles + Tailwind imports)
```

---

## 2. Component Hierarchy (Tree View)

### 2.1 Employee Role Flow

```
App
├── Layout
│   ├── Header (show user name + logout)
│   ├── MainContent
│   │   ├── CheckInPage
│   │   │   └── CheckInScreen
│   │   │       ├── Header
│   │   │       │   ├── ShiftInfo (ca name + time)
│   │   │       │   └── Clock (HH:MM:SS real-time)
│   │   │       ├── StatusBanner
│   │   │       │   ├── IpStatus (with visual indicator)
│   │   │       │   └── GpsStatus
│   │   │       ├── ClientSiteModeToggle
│   │   │       │   ├── Label: "Chấm công ngoài văn phòng"
│   │   │       │   └── Switch (conditional: show if IP invalid)
│   │   │       ├── CameraViewfinder (react-webcam)
│   │   │       │   ├── Video stream (40% screen height)
│   │   │       │   └── ShutterButton (large circle)
│   │   │       ├── PhotoPreview (if captured)
│   │   │       │   ├── Image thumbnail
│   │   │       │   └── "Chụp lại" button
│   │   │       ├── ActionSection
│   │   │       │   └── CheckInConfirmButton (disabled until photo)
│   │   │       └── ErrorAlerts
│   │   │           └── Toast (auto-dismiss)
│   │   │
│   │   ├── HistoryPage
│   │   │   └── AttendanceHistoryScreen
│   │   │       ├── MonthYearPicker
│   │   │       ├── AttendanceList
│   │   │       │   └── AttendanceCard[] (one per day)
│   │   │       │       ├── Date + Status tag
│   │   │       │       ├── Check-in/out times
│   │   │       │       ├── Status badge (ON_TIME/LATE_IN/etc.)
│   │   │       │       ├── Sub-status (PENDING_APPROVAL/APPROVED)
│   │   │       │       └── "Xem chi tiết" link
│   │   │       └── Pagination
│   │   │
│   │   ├── RequestsPage
│   │   │   ├── Tabs (Pending | Approved | Rejected)
│   │   │   └── RequestCard[] (one per request)
│   │   │       ├── Type badge (LATE_IN/EARLY_OUT/ADJUSTMENT)
│   │   │       ├── Status + decision date
│   │   │       ├── Reason / review reason
│   │   │       └── "Gửi lại" button (if rejected)
│   │   │
│   │   └── SettingsPage
│   │       ├── Profile section
│   │       │   ├── Avatar
│   │       │   └── Name + department
│   │       └── Preferences
│   │           └── Notification settings
│   │
│   └── BottomTabNav
│       ├── CheckIn (icon + label)
│       ├── History (icon + label)
│       ├── Requests (icon + label, badge count)
│       └── Settings (icon + label)
```

### 2.2 Leader Role Flow

```
App
├── Layout
│   ├── Header
│   │   ├── Logo
│   │   ├── Title: "Quản lý Nhóm"
│   │   └── NotificationBell (with red dot if unread)
│   ├── Sidebar
│   │   ├── User info
│   │   ├── Dashboard (link)
│   │   ├── Requests (link, with unread count)
│   │   ├── Reports (link)  // TODO: v1.1 stub (Out of MVP scope)
│   │   └── Logout
│   ├── MainContent
│   │   ├── DashboardPage
│   │   │   └── DailyRosterScreen
│   │   │       ├── DatePicker (today by default)
│   │   │       ├── RosterList
│   │   │       │   └── RosterCard[] (one per employee)
│   │   │       │       ├── Employee name + avatar
│   │   │       │       ├── Shift info (ca + times)
│   │   │       │       ├── Status tag (ON_TIME/LATE_IN/INCOMPLETE/ABSENT)
│   │   │       │       │   - Color-coded (green/red/orange/gray)
│   │   │       │       ├── Check-in/out times (if exists)
│   │   │       │       ├── View details button
│   │   │       │       └── Exception badge (if pending request)
│   │   │       └── Summary stats (total checked in, late, etc.)
│   │   │
│   │   ├── RequestsPage
│   │   │   └── PendingRequestsScreen
│   │   │       ├── Tabs (Pending | History)
│   │   │       ├── PendingRequestsList
│   │   │       │   └── RequestCard[] (one per pending request)
│   │   │       │       ├── Employee name
│   │   │       │       ├── Request type badge (LATE_IN/EARLY_OUT/ADJUSTMENT)
│   │   │       │       ├── Timestamp
│   │   │       │       ├── Reason text
│   │   │       │       ├── AttendanceRecord preview
│   │   │       │       └── [Duyệt] [Từ chối] buttons
│   │   │       │
│   │   │       └── RequestDetailModal (when clicked)
│   │   │           ├── Full request details
│   │   │           ├── Employee info
│   │   │           ├── Attendance record photo (if available)
│   │   │           ├── ApprovalForm
│   │   │           │   ├── [Duyệt] button
│   │   │           │   └── [Từ chối] button → RejectionReasonModal
│   │   │           └── Close button
│   │   │
│   │   └── ReportsPage  // TODO: v1.1 stub (Out of MVP scope)
│   │       ├── Date range picker
│   │       └── Summary stats + charts (nice-to-have)
│   │
│   └── NotificationPanel (slide-over / dropdown when bell clicked)
│       ├── Notification list
│       │   └── NotificationItem[] (can mark as read)
│       └── "Mark all as read" button
```

### 2.3 Admin Role Flow

```
App
├── Layout
│   ├── Header
│   │   ├── Logo
│   │   ├── Title: "Cấu hình Hệ thống"
│   │   └── NotificationBell (optional)
│   ├── Sidebar
│   │   ├── User info (ADMIN role indicator)
│   │   ├── Dashboard (link)
│   │   ├── Configuration (expandable menu)
│   │   │   ├── Shifts (link)
│   │   │   ├── IPs (link)
│   │   │   └── Holidays (link)
│   │   ├── Requests (link, with count)
│   │   ├── Audit Logs (link)
│   │   └── Logout
│   ├── MainContent
│   │   ├── ConfigPage (Shifts)
│   │   │   └── ShiftConfigScreen
│   │   │       ├── Add Shift button
│   │   │       ├── ShiftTable (data grid)
│   │   │       │   └── Shift row[] (one per shift)
│   │   │       │       ├── Name (editable on double-click)
│   │   │       │       ├── Start time (editable)
│   │   │       │       ├── End time (editable)
│   │   │       │       ├── Thresholds (late_in, early_out, half_day, ot_buffer)
│   │   │       │       ├── Actions: [Edit] [Delete]
│   │   │       │       └── On save: popup for audit log reason
│   │   │       └── Delete confirmation modal
│   │   │
│   │   ├── ConfigPage (IP Manager)
│   │   │   └── IpManagerScreen
│   │   │       ├── Tabs (Company IPs | Individual IPs)
│   │   │       ├── Add IP button
│   │   │       ├── IpTable (data grid)
│   │   │       │   └── IP row[] (one per IP)
│   │   │       │       ├── IP address (monospace font)
│   │   │       │       ├── Scope (COMPANY / INDIVIDUAL)
│   │   │       │       ├── Employee (if individual scope)
│   │   │       │       ├── Description
│   │   │       │       ├── Created by + date
│   │   │       │       ├── Actions: [Edit] [Delete]
│   │   │       │       └── On delete: confirmation
│   │   │       └── Audit log reason on save
│   │   │
│   │   ├── ConfigPage (Holidays)
│   │   │   └── HolidayManagerScreen
│   │   │       ├── Tabs (Fixed Holidays | Dynamic Holidays)
│   │   │       ├── Add Holiday button
│   │   │       ├── HolidayTable
│   │   │       │   └── Holiday row[] (one per holiday)
│   │   │       │       ├── Date (date picker on edit)
│   │   │       │       ├── Name (e.g., "Tết Dương lịch")
│   │   │       │       ├── Type (FIXED / DYNAMIC)
│   │   │       │       ├── Year (if dynamic)
│   │   │       │       ├── Actions: [Edit] [Delete]
│   │   │       │       └── Audit log on save
│   │   │       └── Delete confirmation
│   │   │
│   │   ├── DashboardPage (Requests/Overrides)
│   │   │   └── AdminRequestsScreen
│   │   │       ├── Tabs (All Requests | Overrides)
│   │   │       ├── Request list (similar to Leader, but all employees)
│   │   │       └── Action: [View Attendance] [Override Directly]
│   │   │
│   │   ├── AttendanceOverridePage
│   │   │   └── AttendanceOverrideForm
│   │   │       ├── Search employee by name/ID
│   │   │       ├── Date picker (which day to override)
│   │   │       ├── Attendance record preview
│   │   │       ├── Field editor (double-click or modal)
│   │   │       │   ├── Check-in time
│   │   │       │   ├── Check-out time
│   │   │       │   ├── Attendance status
│   │   │       │   └── Photo URL (view presigned URL link)
│   │   │       ├── Audit reason (required, not empty)
│   │   │       └── [Save] [Cancel] buttons
│   │   │
│   │   └── AuditLogPage
│   │       └── AuditLogViewer
│   │           ├── Filters
│   │           │   ├── Date range picker
│   │           │   ├── Admin user (dropdown)
│   │           │   ├── Target table (dropdown)
│   │           │   └── Employee ID (search)
│   │           ├── AuditLogTable
│   │           │   └── Audit log row[] (one per change)
│   │           │       ├── Timestamp
│   │           │       ├── Admin who made change
│   │           │       ├── Table/entity affected
│   │           │       ├── Field changed
│   │           │       ├── Old value (monospace)
│   │           │       ├── New value (monospace)
│   │           │       ├── Reason (why)
│   │           │       └── [View diff] (optional, nice-to-have)
│   │           └── Pagination + export option  // TODO: v1.1 stub (Out of MVP scope)
│   │
│   └── NotificationPanel
```

---

## 3. Shared Components

### 3.1 Common UI Components

```typescript
// components/common/

interface HeaderProps {
  title: string;
  subtitle?: string;
  rightAction?: React.ReactNode; // NotificationBell, user menu, etc.
}
export const Header: React.FC<HeaderProps> = ({ ... });

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
  role: 'EMPLOYEE' | 'LEADER' | 'ADMIN';
}
export const Sidebar: React.FC<SidebarProps> = ({ ... });

interface BottomTabNavProps {
  currentTab: 'check-in' | 'history' | 'requests' | 'settings';
  onTabChange: (tab) => void;
  unreadRequestCount?: number;
}
export const BottomTabNav: React.FC<BottomTabNavProps> = ({ ... });

interface ToastProps {
  type: 'success' | 'error' | 'info' | 'warning';
  message: string;
  duration?: number; // default 4000ms
  onClose?: () => void;
}
export const Toast: React.FC<ToastProps> = ({ ... });

interface NotificationBellProps {
  unreadCount: number;
  onBellClick: () => void;
}
export const NotificationBell: React.FC<NotificationBellProps> = ({ ... });

interface ErrorBoundaryProps {
  children: React.ReactNode;
}
export class ErrorBoundary extends React.Component<ErrorBoundaryProps> { ... }
```

### 3.2 Employee-Specific Components

```typescript
// components/employee/

interface CameraViewfinderProps {
  onPhotoCapture: (base64: string) => void;
  onError: (error: string) => void;
  width?: string; // default '100%'
  height?: string; // default '40vh'
}
export const CameraViewfinder: React.FC<CameraViewfinderProps> = ({ ... });

// Renamed MacWifiStatus → IpStatus (ADR-002: pivot MAC Wi-Fi → Public IP)
interface IpStatusProps {
  isConnected: boolean;
  isValid?: boolean;
  onWarning?: (message: string) => void;
}
export const IpStatus: React.FC<IpStatusProps> = ({ ... });

interface GpsStatusProps {
  isAvailable: boolean;
  latitude?: number;
  longitude?: number;
  accuracy?: number;
  onError?: (message: string) => void;
}
export const GpsStatus: React.FC<GpsStatusProps> = ({ ... });

interface ClientSiteModeToggleProps {
  isEnabled: boolean;
  onToggle: (enabled: boolean) => void;
  disabled?: boolean; // true if IP is valid
}
export const ClientSiteModeToggle: React.FC<ClientSiteModeToggleProps> = ({ ... });

interface CheckInScreenProps {
  shiftId: string;
  onSuccess: (record: AttendanceRecord) => void;
  onError: (error: string) => void;
}
export const CheckInScreen: React.FC<CheckInScreenProps> = ({ ... });

interface ExceptionRequestFormProps {
  attendanceRecordId: string;
  recordStatus: 'LATE_IN' | 'EARLY_OUT' | 'HALF_DAY';
  onSuccess: () => void;
}
export const ExceptionRequestForm: React.FC<ExceptionRequestFormProps> = ({ ... });
```

### 3.3 Leader-Specific Components

```typescript
// components/leader/

interface RosterCardProps {
  employee: EmployeeInfo;
  attendanceRecord?: AttendanceRecord;
  pendingRequest?: ExceptionRequest;
  onViewDetails: () => void;
}
export const RosterCard: React.FC<RosterCardProps> = ({ ... });

interface RequestDetailModalProps {
  isOpen: boolean;
  request: ExceptionRequest | AdjustmentRequest;
  onApprove: () => void;
  onReject: (reason: string) => void;
  onClose: () => void;
}
export const RequestDetailModal: React.FC<RequestDetailModalProps> = ({ ... });

interface ApprovalFormProps {
  requestId: string;
  requestType: 'EXCEPTION' | 'ADJUSTMENT';
  onApproveClick: () => void;
  onRejectClick: () => void;
}
export const ApprovalForm: React.FC<ApprovalFormProps> = ({ ... });

interface RejectionReasonModalProps {
  isOpen: boolean;
  onSubmit: (reason: string) => void;
  onCancel: () => void;
}
export const RejectionReasonModal: React.FC<RejectionReasonModalProps> = ({ ... });
```

### 3.4 Admin-Specific Components

```typescript
// components/admin/

interface ShiftConfigFormProps {
  shift?: Shift; // undefined = add new
  onSave: (shift: Shift, auditReason: string) => void;
  onCancel: () => void;
}
export const ShiftConfigForm: React.FC<ShiftConfigFormProps> = ({ ... });

// Renamed MacManagerGrid → IpManagerGrid (ADR-002)
interface IpManagerGridProps {
  scope: 'COMPANY' | 'INDIVIDUAL';
  onAddIp: (ip: ValidIp, auditReason: string) => void;
  onDeleteIp: (ipId: string, auditReason: string) => void;
  onEditIp: (ip: ValidIp, auditReason: string) => void;
}
export const IpManagerGrid: React.FC<IpManagerGridProps> = ({ ... });

interface AttendanceOverrideFormProps {
  employeeId: string;
  date: Date;
  onSave: (changes: OverrideChanges, reason: string) => void;
  onCancel: () => void;
}
export const AttendanceOverrideForm: React.FC<AttendanceOverrideFormProps> = ({ ... });

interface AuditLogViewerProps {
  filters?: AuditLogFilter;
  onFilterChange: (filter: AuditLogFilter) => void;
}
export const AuditLogViewer: React.FC<AuditLogViewerProps> = ({ ... });
```

---

## 4. State Management Strategy

### 4.1 Auth State (Zustand or Context)
```typescript
// store/authStore.ts
interface AuthState {
  user: User | null;
  token: string | null;
  role: 'EMPLOYEE' | 'LEADER' | 'ADMIN';
  login: (credentials) => Promise<void>;
  logout: () => void;
  refreshToken: () => Promise<void>;
  isLoading: boolean;
}

export const useAuthStore = create<AuthState>((set) => ({ ... }));
```

### 4.2 UI State (Zustand)
```typescript
// store/uiStore.ts
interface UIState {
  currentPage: string;
  isSidebarOpen: boolean;
  toastMessage?: ToastMessage;
  notificationPanelOpen: boolean;
  // ... add/remove/update methods
}

export const useUIStore = create<UIState>((set) => ({ ... }));
```

### 4.3 Server State (TanStack Query)
```typescript
// hooks/useAttendance.ts
export const useAttendance = (employeeId?: string) => {
  return useQuery({
    queryKey: ['attendance', employeeId],
    queryFn: () => attendanceService.getToday(employeeId),
    staleTime: 1000 * 60 * 5, // 5 minutes
  });
};

export const useNotifications = () => {
  return useQuery({
    queryKey: ['notifications'],
    queryFn: () => notificationService.getPending(),
    refetchInterval: 15 * 1000, // poll every 15 seconds
  });
};
```

---

## 5. Custom Hooks

### 5.1 Camera Hook
```typescript
// hooks/useCamera.ts
export const useCamera = () => {
  const [photo, setPhoto] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const capturePhoto = async (stream: MediaStream) => {
    // Use react-webcam to capture, compress, convert to base64
  };

  const clearPhoto = () => setPhoto(null);

  return { photo, error, isLoading, capturePhoto, clearPhoto };
};
```

### 5.2 Geolocation Hook
```typescript
// hooks/useGeolocation.ts
export const useGeolocation = (options?: PositionOptions) => {
  const [coords, setCoords] = useState<GeolocationCoordinates | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const getLocation = () => {
    setIsLoading(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => { setCoords(pos.coords); setIsLoading(false); },
      (err) => { setError(err.message); setIsLoading(false); },
      options
    );
  };

  return { coords, error, isLoading, getLocation };
};
```

### 5.3 IP Validation Hook
```typescript
// hooks/useIpValidation.ts  (Renamed from useWifiMac — ADR-002)
export const useIpValidation = () => {
  const [ipValid, setIpValid] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Backend resolves Public IP from X-Forwarded-For header and validates against valid_ips table
    attendanceService.validateOfficeIp()
      .then((res) => setIpValid(res.valid))
      .catch(() => setIpValid(false));
  }, []);

  return { ipValid, error };
};
```

---

## 6. Form Handling

Use **React Hook Form + Zod** for validation:

```typescript
// types/form.ts
import { z } from 'zod';

// Note: macAddress removed from form — Public IP resolved server-side (ADR-002)
export const CheckInFormSchema = z.object({
  latitude: z.number().optional(),
  longitude: z.number().optional(),
  photoBase64: z.string().min(1000, 'Photo is required'),
  isClientSite: z.boolean(),
});

export type CheckInFormData = z.infer<typeof CheckInFormSchema>;

// In component:
const { register, handleSubmit, watch, formState: { errors } } = useForm<CheckInFormData>({
  resolver: zodResolver(CheckInFormSchema),
});

const onSubmit = handleSubmit(async (data) => {
  // Submit to API
});
```

---

## 7. Routing Setup (React Router v6)

```typescript
// App.tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';

function App() {
  const { user, role } = useAuthStore();

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        {user && role === 'EMPLOYEE' && (
          <Route element={<EmployeeLayout />}>
            <Route path="/check-in" element={<CheckInPage />} />
            <Route path="/history" element={<HistoryPage />} />
            <Route path="/requests" element={<RequestsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        )}

        {user && role === 'LEADER' && (
          <Route element={<LeaderLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/requests" element={<RequestsPage />} />
            {/* TODO: v1.1 stub (Out of MVP scope) */}
            <Route path="/reports" element={<ReportsPage />} />
          </Route>
        )}

        {user && role === 'ADMIN' && (
          <Route element={<AdminLayout />}>
            <Route path="/config/shifts" element={<ShiftConfigPage />} />
            <Route path="/config/ips" element={<IpConfigPage />} />
            <Route path="/config/holidays" element={<HolidayConfigPage />} />
            <Route path="/requests" element={<RequestsPage />} />
            <Route path="/audit-logs" element={<AuditLogsPage />} />
          </Route>
        )}

        <Route path="/" element={<Navigate to={role === 'EMPLOYEE' ? '/check-in' : '/dashboard'} />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
```

---

## Next: UI States & State Machine Mapping

See document **06-ui-state-mapping.md** for how each UI state correlates with PRD State Machine.
