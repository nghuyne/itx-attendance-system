# Leader Dashboard Specification

**Target:** Responsive (Tablet/Desktop)  
**Primary User:** Team Leader (Trưởng nhóm)  
**Related PRD Features:** FR-6, FR-11, FR-12, FR-13, FR-14

---

## 1. Screen Layout (Responsive Dashboard)

### 1.1 Desktop Layout (1024px+)

```
┌────────────────────────────────────────────────────────────────┐
│ Logo        Quản lý Nhóm                     🔔(2) [User Menu] │  ← Header
├────────┬──────────────────────────────────────────────────────┤
│ [Menu] │ 📅 2026-06-04   [Ngày hôm qua] [Hôm nay] [Ngày mai] │
│ ■ Dash │                                                      │
│ ■ Req. │ ┌─────────────────────────────────────────────────┐ │
│ ■ Rpt. │ │  Nhân viên: Minh | Giờ: 9:00-17:30             │ │
│ ■ Exit │ │  [✓] 08:45 — Đúng giờ                          │ │
│        │ │  17:30 — (chưa check-out)                       │ │
│        │ │  [Xem chi tiết]                                 │ │
│        │ └─────────────────────────────────────────────────┘ │
│        │                                                      │
│        │ ┌─────────────────────────────────────────────────┐ │
│        │ │  Nhân viên: Lan | Giờ: 9:00-17:30             │ │
│        │ │  [⚠️] 09:20 — Đi muộn 20 phút                   │ │
│        │ │  [Yêu cầu Ngoại lệ PENDING]                    │ │
│        │ │  [Duyệt] [Từ chối]                             │ │
│        │ └─────────────────────────────────────────────────┘ │
│        │                                                      │
│        │ [Show more employees...]                            │
│        │                                                      │
│        │ Summary:                                             │
│        │ ✓ On Time: 8  |  ⚠️ Late: 2  |  ❌ Absent: 1      │
└────────┴──────────────────────────────────────────────────────┘
```

### 1.2 Mobile Layout (< 768px)

```
┌────────────────────────────────────┐
│ ☰ Menu  Quản lý Nhóm    🔔(2)  ⋮ │  ← Header
├────────────────────────────────────┤
│ [📅 Ngày hôm nay]                 │
├────────────────────────────────────┤
│ [Dashboard] [Yêu cầu] [Báo cáo]   │  ← Tab navigation
├────────────────────────────────────┤
│ ┌──────────────────────────────────┐│
│ │ Minh | 08:45 ✓ Đúng giờ        ││
│ │ [Xem chi tiết]                  ││
│ └──────────────────────────────────┘│
│ ┌──────────────────────────────────┐│
│ │ Lan | 09:20 ⚠️ Đi muộn [PENDING]││
│ │ [Duyệt] [Từ chối]               ││
│ └──────────────────────────────────┘│
│ [Load more]                         │
└────────────────────────────────────┘
```

---

## 2. Component Breakdown

### 2.1 Sidebar Navigation (Desktop Only)

**Position:** Fixed left, ~200px width  
**Collapse:** Hamburger menu on tablet

| Item | Icon | Route | Badge |
|---|---|---|---|
| Dashboard | 📊 | `/leader/dashboard` | — |
| Pending Requests | 📋 | `/leader/requests` | Count (red badge) |
| Team Reports | 📈 | `/leader/reports` | — |
| Settings | ⚙️ | `/leader/settings` | — |
| Logout | 🚪 | `/auth/logout` | — |

**Component:**
```tsx
interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

export const LeaderSidebar: React.FC<SidebarProps> = ({ isOpen, onClose }) => {
  const [unreadCount, setUnreadCount] = useState(0);

  // Poll for unread count
  useEffect(() => {
    const interval = setInterval(async () => {
      const count = await requestService.getPendingCount();
      setUnreadCount(count);
    }, 15000); // 15 seconds

    return () => clearInterval(interval);
  }, []);

  return (
    <aside
      className={`fixed md:static left-0 top-0 w-64 h-screen bg-slate-900 text-white flex flex-col transition-transform md:translate-x-0 ${
        isOpen ? 'translate-x-0' : '-translate-x-full'
      }`}
    >
      {/* Close button on mobile */}
      <button className="md:hidden p-4 text-right" onClick={onClose}>
        ✕
      </button>

      {/* Navigation */}
      <nav className="flex-1 space-y-2 p-4">
        <NavLink to="/leader/dashboard" icon="📊" label="Dashboard" />
        <NavLink
          to="/leader/requests"
          icon="📋"
          label="Yêu cầu"
          badge={unreadCount > 0 ? unreadCount : undefined}
        />
        <NavLink to="/leader/reports" icon="📈" label="Báo cáo" />
        <NavLink to="/leader/settings" icon="⚙️" label="Cài đặt" />
      </nav>

      {/* User info + logout */}
      <div className="border-t border-slate-700 p-4">
        <button className="w-full text-left py-2 hover:bg-slate-800 rounded px-2">
          Đăng xuất
        </button>
      </div>
    </aside>
  );
};

interface NavLinkProps {
  to: string;
  icon: string;
  label: string;
  badge?: number;
}

const NavLink: React.FC<NavLinkProps> = ({ to, icon, label, badge }) => {
  const isActive = useLocation().pathname === to;

  return (
    <Link
      to={to}
      className={`flex items-center gap-3 px-4 py-2 rounded transition ${
        isActive ? 'bg-emerald-600 text-white' : 'text-slate-300 hover:bg-slate-800'
      }`}
    >
      <span>{icon}</span>
      <span>{label}</span>
      {badge && (
        <span className="ml-auto bg-red-600 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">
          {badge}
        </span>
      )}
    </Link>
  );
};
```

---

### 2.2 Header (Notification Bell)

**Position:** Top right corner  
**Sticky:** Yes  
**Contains:**
- Logo + title
- Bell icon with unread badge
- User dropdown menu

**Component:**
```tsx
interface HeaderProps {
  unreadCount: number;
  onBellClick: () => void;
}

export const LeaderHeader: React.FC<HeaderProps> = ({ unreadCount, onBellClick }) => {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <header className="bg-white border-b border-slate-200 sticky top-0 z-20">
      <div className="flex items-center justify-between px-4 py-3 md:px-6">
        {/* Left: Logo + Title */}
        <div className="flex items-center gap-2">
          <h1 className="text-lg md:text-2xl font-bold text-slate-900">Quản lý Nhóm</h1>
        </div>

        {/* Right: Bell + Menu */}
        <div className="flex items-center gap-4">
          {/* Notification Bell */}
          <button
            onClick={onBellClick}
            className="relative w-8 h-8 md:w-10 md:h-10 flex items-center justify-center hover:bg-slate-100 rounded-lg transition"
            aria-label="Notifications"
          >
            <Bell className="w-6 h-6" />
            {unreadCount > 0 && (
              <span className="absolute top-0 right-0 w-5 h-5 bg-red-600 text-white text-xs rounded-full flex items-center justify-center">
                {unreadCount}
              </span>
            )}
          </button>

          {/* User Menu */}
          <div className="relative">
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="p-2 hover:bg-slate-100 rounded-lg transition"
            >
              ⋮
            </button>
            {menuOpen && (
              <div className="absolute right-0 mt-2 bg-white border border-slate-200 rounded-lg shadow-lg">
                <Link to="/leader/settings" className="block px-4 py-2 hover:bg-slate-50">
                  Cài đặt
                </Link>
                <button className="w-full text-left px-4 py-2 hover:bg-slate-50 text-red-600">
                  Đăng xuất
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};
```

---

### 2.3 Daily Roster (Main Content)

**Purpose:** Show all team members' attendance for the selected date  
**Default:** Today's date  
**Features:**
- Date picker (navigation: yesterday, today, tomorrow buttons)
- Roster cards (one per employee)
- Summary stats (total on-time, late, absent)

**Status Color Codes:**
- `ON_TIME` / `APPROVED`: Green badge
- `LATE_IN` / `EARLY_OUT` / `PENDING_APPROVAL`: Red/Amber badge
- `ABSENT`: Gray badge
- `INCOMPLETE`: Orange badge

**Component:**
```tsx
interface DailyRosterScreenProps {
  onRequestClick: (request: ExceptionRequest) => void;
}

export const DailyRosterScreen: React.FC<DailyRosterScreenProps> = ({ onRequestClick }) => {
  const [selectedDate, setSelectedDate] = useState(new Date());
  const [roster, setRoster] = useState<RosterItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const queryDate = selectedDate.toISOString().split('T')[0];

  // Fetch roster on date change
  useEffect(() => {
    const fetchRoster = async () => {
      setIsLoading(true);
      try {
        const data = await leaderService.getDailyRoster(queryDate);
        setRoster(data);
      } catch (err) {
        console.error('Lỗi khi tải bảng công', err);
      } finally {
        setIsLoading(false);
      }
    };

    fetchRoster();
  }, [queryDate]);

  const stats = {
    onTime: roster.filter((r) => r.status === 'ON_TIME' || r.subStatus === 'APPROVED').length,
    late: roster.filter((r) => r.status === 'LATE_IN' && r.subStatus !== 'APPROVED').length,
    absent: roster.filter((r) => r.status === 'ABSENT').length,
    incomplete: roster.filter((r) => r.status === 'INCOMPLETE').length,
  };

  const goToPreviousDay = () => {
    setSelectedDate((prev) => new Date(prev.getTime() - 24 * 60 * 60 * 1000));
  };

  const goToToday = () => {
    setSelectedDate(new Date());
  };

  const goToNextDay = () => {
    setSelectedDate((prev) => new Date(prev.getTime() + 24 * 60 * 60 * 1000));
  };

  const formattedDate = selectedDate.toLocaleDateString('vi-VN', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

  return (
    <div className="space-y-4 p-4 max-w-4xl mx-auto">
      {/* Date Navigation */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold">{formattedDate}</h2>
        <div className="flex gap-2">
          <button onClick={goToPreviousDay} className="px-3 py-1 bg-slate-100 rounded hover:bg-slate-200">
            ← Ngày trước
          </button>
          <button
            onClick={goToToday}
            className={`px-3 py-1 rounded ${
              new Date().toDateString() === selectedDate.toDateString()
                ? 'bg-emerald-600 text-white'
                : 'bg-slate-100 hover:bg-slate-200'
            }`}
          >
            Hôm nay
          </button>
          <button onClick={goToNextDay} className="px-3 py-1 bg-slate-100 rounded hover:bg-slate-200">
            Ngày sau →
          </button>
        </div>
      </div>

      {/* Loading State */}
      {isLoading && <div className="text-center py-8">Đang tải...</div>}

      {/* Roster Cards */}
      {!isLoading && roster.length > 0 && (
        <div className="space-y-3">
          {roster.map((item) => (
            <RosterCard
              key={item.employeeId}
              item={item}
              onRequestAction={() => item.pendingRequest && onRequestClick(item.pendingRequest)}
            />
          ))}
        </div>
      )}

      {/* Empty State */}
      {!isLoading && roster.length === 0 && (
        <div className="text-center py-8 text-slate-600">Không có nhân viên trong nhóm</div>
      )}

      {/* Summary Stats */}
      {!isLoading && roster.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 bg-slate-50 p-4 rounded-lg">
          <div className="text-center">
            <div className="text-2xl font-bold text-emerald-600">{stats.onTime}</div>
            <div className="text-xs text-slate-600">Đúng giờ</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-red-600">{stats.late}</div>
            <div className="text-xs text-slate-600">Đi muộn</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-amber-600">{stats.incomplete}</div>
            <div className="text-xs text-slate-600">Chưa hoàn chỉnh</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-slate-600">{stats.absent}</div>
            <div className="text-xs text-slate-600">Vắng mặt</div>
          </div>
        </div>
      )}
    </div>
  );
};

interface RosterCardProps {
  item: RosterItem;
  onRequestAction?: () => void;
}

const RosterCard: React.FC<RosterCardProps> = ({ item, onRequestAction }) => {
  const statusColor = {
    ON_TIME: 'bg-green-50 border-green-300',
    LATE_IN: 'bg-red-50 border-red-300',
    EARLY_OUT: 'bg-amber-50 border-amber-300',
    INCOMPLETE: 'bg-amber-50 border-amber-300',
    ABSENT: 'bg-slate-50 border-slate-300',
  };

  const statusBadgeColor = {
    ON_TIME: 'bg-green-100 text-green-700',
    LATE_IN: 'bg-red-100 text-red-700',
    EARLY_OUT: 'bg-amber-100 text-amber-700',
    INCOMPLETE: 'bg-amber-100 text-amber-700',
    ABSENT: 'bg-slate-100 text-slate-700',
  };

  const statusIcon = {
    ON_TIME: '✓',
    LATE_IN: '⚠️',
    EARLY_OUT: '⚠️',
    INCOMPLETE: '?',
    ABSENT: '✗',
  };

  return (
    <div className={`border-l-4 rounded-lg p-4 ${statusColor[item.status]}`}>
      <div className="flex items-start justify-between mb-2">
        <div>
          <h3 className="font-bold text-slate-900">{item.employeeName}</h3>
          <p className="text-sm text-slate-600">
            {item.shiftName}: {item.shiftStartTime}–{item.shiftEndTime}
          </p>
        </div>
        <span className={`px-3 py-1 rounded text-sm font-medium ${statusBadgeColor[item.status]}`}>
          {statusIcon[item.status]} {item.status}
        </span>
      </div>

      {item.checkInTime && (
        <p className="text-sm text-slate-700 mb-1">
          📍 Check-in: {item.checkInTime}
          {item.checkOutTime && ` — Check-out: ${item.checkOutTime}`}
        </p>
      )}

      {/* Pending Request Badge */}
      {item.pendingRequest && (
        <div className="mt-2 flex items-center gap-2">
          <span className="bg-red-500 text-white text-xs px-2 py-1 rounded font-medium">
            Yêu cầu {item.pendingRequest.type} PENDING
          </span>
          <button
            onClick={onRequestAction}
            className="text-blue-600 text-sm font-medium hover:underline"
          >
            Xem chi tiết
          </button>
        </div>
      )}

      <button className="mt-3 text-blue-600 text-sm font-medium hover:underline">
        Xem chi tiết
      </button>
    </div>
  );
};

interface RosterItem {
  employeeId: string;
  employeeName: string;
  shiftName: string;
  shiftStartTime: string;
  shiftEndTime: string;
  status: 'ON_TIME' | 'LATE_IN' | 'EARLY_OUT' | 'INCOMPLETE' | 'ABSENT';
  subStatus?: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
  checkInTime?: string;
  checkOutTime?: string;
  pendingRequest?: ExceptionRequest;
}
```

---

### 2.4 Pending Requests Screen

**Purpose:** Show all pending Exception & Adjustment Requests from team members  
**Tabs:** Pending | Approved | Rejected  
**Sorting:** Most recent first  
**Action:** Click card → RequestDetailModal

**Component:**
```tsx
interface PendingRequestsScreenProps {
  onRequestClick: (request: ExceptionRequest | AdjustmentRequest) => void;
}

export const PendingRequestsScreen: React.FC<PendingRequestsScreenProps> = ({ onRequestClick }) => {
  const [tab, setTab] = useState<'pending' | 'approved' | 'rejected'>('pending');
  const [requests, setRequests] = useState<(ExceptionRequest | AdjustmentRequest)[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const fetchRequests = async () => {
      setIsLoading(true);
      try {
        const data = await leaderService.getRequests(tab);
        setRequests(data);
      } catch (err) {
        console.error('Lỗi khi tải yêu cầu', err);
      } finally {
        setIsLoading(false);
      }
    };

    fetchRequests();
  }, [tab]);

  const pendingCount = requests.filter((r) => r.status === 'PENDING').length;

  return (
    <div className="p-4 max-w-4xl mx-auto space-y-4">
      {/* Tabs */}
      <div className="flex gap-2 border-b border-slate-200">
        {(['pending', 'approved', 'rejected'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 border-b-2 font-medium transition ${
              tab === t
                ? 'border-emerald-600 text-emerald-600'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            {t === 'pending' ? `Chờ duyệt (${pendingCount})` : t === 'approved' ? 'Đã duyệt' : 'Từ chối'}
          </button>
        ))}
      </div>

      {/* Loading */}
      {isLoading && <div className="text-center py-8">Đang tải...</div>}

      {/* Request Cards */}
      {!isLoading && requests.length > 0 && (
        <div className="space-y-3">
          {requests.map((req) => (
            <RequestCard
              key={req.id}
              request={req}
              onClick={() => onRequestClick(req)}
            />
          ))}
        </div>
      )}

      {!isLoading && requests.length === 0 && (
        <div className="text-center py-8 text-slate-600">
          {tab === 'pending' ? 'Không có yêu cầu chờ duyệt' : 'Danh sách trống'}
        </div>
      )}
    </div>
  );
};

const RequestCard: React.FC<{
  request: ExceptionRequest | AdjustmentRequest;
  onClick: () => void;
}> = ({ request, onClick }) => {
  return (
    <div className="border border-slate-200 rounded-lg p-4 hover:shadow-md transition cursor-pointer" onClick={onClick}>
      <div className="flex items-start justify-between mb-2">
        <div>
          <h3 className="font-bold">{request.employee?.name}</h3>
          <p className="text-sm text-slate-600">{formatDate(request.createdAt)}</p>
        </div>
        <span className={`px-2 py-1 rounded text-sm font-medium ${
          request.status === 'PENDING' ? 'bg-amber-100 text-amber-700' :
          request.status === 'APPROVED' ? 'bg-green-100 text-green-700' :
          'bg-red-100 text-red-700'
        }`}>
          {request.status}
        </span>
      </div>

      <p className="text-sm mb-2">
        <span className="font-medium">Loại:</span> {'requestType' in request ? request.requestType : 'ADJUSTMENT'}
      </p>
      <p className="text-sm text-slate-700">{request.reason}</p>

      {request.status === 'PENDING' && <p className="mt-2 text-blue-600 text-sm font-medium">→ Xem chi tiết</p>}
    </div>
  );
};
```

---

### 2.5 Request Detail Modal (Approval Flow)

**Triggered:** Click on request card  
**Contents:**
- Full request details + employee info
- Attendance record preview
- Approval form (Duyệt / Từ chối buttons)
- Rejection reason modal (if Từ chối clicked)

**Component:**
```tsx
interface RequestDetailModalProps {
  isOpen: boolean;
  request: ExceptionRequest | AdjustmentRequest;
  onApprove: () => Promise<void>;
  onReject: (reason: string) => Promise<void>;
  onClose: () => void;
}

export const RequestDetailModal: React.FC<RequestDetailModalProps> = ({
  isOpen,
  request,
  onApprove,
  onReject,
  onClose,
}) => {
  const [showRejectReason, setShowRejectReason] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleApprove = async () => {
    setIsSubmitting(true);
    try {
      await onApprove();
      onClose();
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReject = async () => {
    if (!rejectReason.trim()) {
      alert('Vui lòng nhập lý do từ chối');
      return;
    }

    setIsSubmitting(true);
    try {
      await onReject(rejectReason);
      onClose();
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-slate-200 sticky top-0 bg-white">
          <h2 className="text-xl font-bold">Chi tiết Yêu cầu</h2>
          <button onClick={onClose} className="text-slate-600 hover:text-slate-900">
            ✕
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-6">
          {/* Employee + Request Info */}
          <div>
            <h3 className="font-bold mb-2">Nhân viên</h3>
            <p>{request.employee?.name}</p>
            <p className="text-sm text-slate-600">{request.employee?.department}</p>
          </div>

          <div>
            <h3 className="font-bold mb-2">Loại Yêu cầu</h3>
            <p>{'requestType' in request ? request.requestType : 'Điều chỉnh'}</p>
          </div>

          <div>
            <h3 className="font-bold mb-2">Lý do</h3>
            <p className="bg-slate-50 p-3 rounded text-slate-800">{request.reason}</p>
          </div>

          <div>
            <h3 className="font-bold mb-2">Bản ghi Chấm công</h3>
            <div className="bg-slate-50 p-3 rounded space-y-1">
              <p>📅 {formatDate(request.attendanceRecord?.date)}</p>
              <p>⏰ {request.attendanceRecord?.checkInTime} — {request.attendanceRecord?.checkOutTime || '(chưa check-out)'}</p>
              <p>Trạng thái: {request.attendanceRecord?.attendanceStatus}</p>
              {request.attendanceRecord?.checkInPhotoUrl && (
                <p>
                  📸{' '}
                  <a href={request.attendanceRecord.checkInPhotoUrl} className="text-blue-600 underline">
                    Xem ảnh
                  </a>
                </p>
              )}
            </div>
          </div>

          {/* Approval Form */}
          {request.status === 'PENDING' && (
            <div className="flex gap-3">
              <button
                onClick={handleApprove}
                disabled={isSubmitting}
                className="flex-1 px-4 py-2 bg-green-600 text-white rounded-lg font-medium hover:bg-green-700 disabled:opacity-50"
              >
                {isSubmitting ? 'Đang xử lý...' : '✓ Duyệt'}
              </button>
              <button
                onClick={() => setShowRejectReason(true)}
                disabled={isSubmitting}
                className="flex-1 px-4 py-2 border border-red-600 text-red-600 rounded-lg font-medium hover:bg-red-50 disabled:opacity-50"
              >
                ✗ Từ chối
              </button>
            </div>
          )}

          {/* Rejection Reason Modal */}
          {showRejectReason && (
            <div className="border-2 border-red-300 bg-red-50 p-4 rounded-lg space-y-3">
              <label className="block">
                <span className="font-medium text-red-900 mb-2 block">Lý do từ chối (bắt buộc)</span>
                <textarea
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="Nhập lý do từ chối..."
                  className="w-full border border-red-300 rounded p-2 focus:outline-none focus:ring-2 focus:ring-red-500"
                  rows={3}
                />
              </label>
              <div className="flex gap-3">
                <button
                  onClick={handleReject}
                  disabled={!rejectReason.trim() || isSubmitting}
                  className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg font-medium hover:bg-red-700 disabled:opacity-50"
                >
                  {isSubmitting ? '...' : 'Xác nhận từ chối'}
                </button>
                <button
                  onClick={() => setShowRejectReason(false)}
                  disabled={isSubmitting}
                  className="flex-1 px-4 py-2 border border-slate-300 rounded-lg font-medium hover:bg-slate-50"
                >
                  Hủy
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
```

---

## 3. Notification Panel (Slide-over)

**Trigger:** Click bell icon in header  
**Position:** Right side, slide-in from right  
**Width:** ~400px on desktop, full width on mobile  
**Auto-dismiss:** Close on Escape key

**Component:**
```tsx
interface NotificationPanelProps {
  isOpen: boolean;
  onClose: () => void;
  notifications: Notification[];
  onMarkAsRead: (notificationId: string) => void;
  onMarkAllAsRead: () => void;
}

export const NotificationPanel: React.FC<NotificationPanelProps> = ({
  isOpen,
  onClose,
  notifications,
  onMarkAsRead,
  onMarkAllAsRead,
}) => {
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [onClose]);

  return (
    <>
      {/* Backdrop */}
      {isOpen && (
        <div className="fixed inset-0 bg-black/30 z-40" onClick={onClose} />
      )}

      {/* Panel */}
      <div
        className={`fixed right-0 top-0 h-screen w-full md:w-96 bg-white shadow-2xl transform transition-transform z-50 ${
          isOpen ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-slate-200 sticky top-0 bg-white">
          <h2 className="font-bold">Thông báo</h2>
          <div className="flex gap-2">
            <button
              onClick={onMarkAllAsRead}
              className="text-blue-600 text-sm font-medium hover:underline"
            >
              Đánh dấu tất cả
            </button>
            <button
              onClick={onClose}
              className="text-slate-600 hover:text-slate-900"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Notifications List */}
        <div className="overflow-y-auto space-y-2 p-4">
          {notifications.length === 0 ? (
            <p className="text-center text-slate-600 py-8">Không có thông báo mới</p>
          ) : (
            notifications.map((notif) => (
              <div
                key={notif.id}
                className={`border rounded-lg p-3 cursor-pointer transition ${
                  notif.isRead ? 'bg-slate-50 border-slate-200' : 'bg-blue-50 border-blue-300'
                }`}
                onClick={() => onMarkAsRead(notif.id)}
              >
                <p className="text-sm font-medium">{notif.title}</p>
                <p className="text-sm text-slate-600 mt-1">{notif.message}</p>
                <p className="text-xs text-slate-500 mt-2">{formatDistanceToNow(notif.createdAt)}</p>
              </div>
            ))
          )}
        </div>
      </div>
    </>
  );
};
```

---

## 4. State Machine Mapping

See document **06-ui-state-mapping.md** for how Leader Dashboard states map to PRD State Machine.

---

## 5. Polling & Real-time Updates

**TanStack Query Setup:**
```typescript
// hooks/useLeaderRequests.ts
export const useLeaderRequests = () => {
  return useQuery({
    queryKey: ['leader-requests'],
    queryFn: () => leaderService.getRequests('pending'),
    refetchInterval: 15 * 1000, // 15 seconds
    refetchOnWindowFocus: true,
  });
};

export const useLeaderNotifications = () => {
  return useQuery({
    queryKey: ['leader-notifications'],
    queryFn: () => notificationService.getPending(),
    refetchInterval: 15 * 1000,
    refetchOnWindowFocus: true,
  });
};
```

**No WebSocket required** for MVP — polling every 15 seconds is sufficient for internal tool.
