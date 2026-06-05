# Admin Settings Specification

**Target:** Desktop (1024px+)  
**Primary User:** Admin (Quản trị viên)  
**Related PRD Features:** FR-6, FR-17, FR-18, FR-19, FR-20

> **[OUT OF SCOPE — MVP]** Tính năng UI để vẽ vùng GPS (GPS Zone Management) được dời sang phase **v1.1**. Backend vẫn hỗ trợ API nhưng UI tạm ẩn để tiết kiệm thời gian phát triển. Dev Agent không cần implement màn hình GPS Zone trong sprint này.

---

## 1. Screen Layout (Desktop-First)

### 1.1 Configuration Dashboard

```
┌────────────────────────────────────────────────────────────────┐
│ Logo        Cấu hình Hệ thống                  🔔(0) [User Menu]│  ← Header
├────────┬──────────────────────────────────────────────────────┤
│ [Config] │ [Cấu hình Ca] [MAC] [Ngày lễ]                      │
│ ▼ Shifts │                                                     │
│   ▼ MACs │ ┌─────────────────────────────────────────────────┐│
│   ▼ Hday │ │ + Thêm ca làm việc                              ││
│ [Requests]│                                                    ││
│ [Audit]  │ ┌──────────────────┬──────────┬──────────┬────────┐││
│ [Logout] │ │ Tên ca │ Giờ bắt │ Giờ kết │ Ngưỡng...│ Hành động
│          │ ├──────────────────┼──────────┼──────────┼────────┤││
│          │ │ Ca Sáng│ 9:00   │ 17:30  │ 0/0/30/ │ [Edit] ││
│          │ │        │        │        │ 30     │ [Del]  ││
│          │ ├──────────────────┼──────────┼──────────┼────────┤││
│          │ │ Ca Chiều│18:00  │ 22:30  │ 0/0/30/ │ [Edit] ││
│          │ │        │        │        │ 30     │ [Del]  ││
│          │ └──────────────────┴──────────┴──────────┴────────┘││
│          │                                                     │
│          │ [Load more]                                         │
│          │                                                     │
└────────┴──────────────────────────────────────────────────────┘
```

---

## 2. Component Breakdown

### 2.1 Config Sidebar Navigation

**Position:** Fixed left, ~180px width (can collapse to icons)  
**Tabs:** Shifts | MACs | Holidays

**Component:**
```tsx
interface AdminSidebarProps {
  activeTab: 'shifts' | 'ips' | 'holidays' | 'requests' | 'audit';
  onTabChange: (tab: string) => void;
}

export const AdminSidebar: React.FC<AdminSidebarProps> = ({ activeTab, onTabChange }) => {
  return (
    <aside className="w-48 bg-slate-900 text-white flex flex-col p-4 h-screen sticky top-0">
      <div className="mb-6">
        <h2 className="text-lg font-bold">Cấu hình</h2>
      </div>

      <nav className="space-y-2 flex-1">
        <NavItem
          icon="⏰"
          label="Ca làm việc"
          active={activeTab === 'shifts'}
          onClick={() => onTabChange('shifts')}
        />
        <NavItem
          icon="📡"
          label="IP hợp lệ"
          active={activeTab === 'ips'}
          onClick={() => onTabChange('ips')}
        />
        <NavItem
          icon="📅"
          label="Ngày lễ"
          active={activeTab === 'holidays'}
          onClick={() => onTabChange('holidays')}
        />
        <hr className="my-4 border-slate-700" />
        <NavItem
          icon="📋"
          label="Yêu cầu"
          active={activeTab === 'requests'}
          onClick={() => onTabChange('requests')}
        />
        <NavItem
          icon="📊"
          label="Audit Log"
          active={activeTab === 'audit'}
          onClick={() => onTabChange('audit')}
        />
      </nav>

      <div className="border-t border-slate-700 pt-4">
        <button className="w-full text-left py-2 hover:bg-slate-800 rounded px-2 text-sm">
          Đăng xuất
        </button>
      </div>
    </aside>
  );
};

interface NavItemProps {
  icon: string;
  label: string;
  active: boolean;
  onClick: () => void;
}

const NavItem: React.FC<NavItemProps> = ({ icon, label, active, onClick }) => (
  <button
    onClick={onClick}
    className={`w-full text-left px-3 py-2 rounded flex items-center gap-3 transition ${
      active ? 'bg-emerald-600 text-white' : 'text-slate-300 hover:bg-slate-800'
    }`}
  >
    <span>{icon}</span>
    <span>{label}</span>
  </button>
);
```

---

### 2.2 Shift Configuration Grid

**Purpose:** CRUD operations on Shift records  
**Action:** Double-click row to edit; Save requires audit reason  
**Delete:** Confirmation modal + audit reason

**Component:**
```tsx
interface ShiftConfigGridProps {
  onSave: (shift: Shift, auditReason: string) => Promise<void>;
  onDelete: (shiftId: string, auditReason: string) => Promise<void>;
}

export const ShiftConfigGrid: React.FC<ShiftConfigGridProps> = ({ onSave, onDelete }) => {
  const [shifts, setShifts] = useState<Shift[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [editingShiftId, setEditingShiftId] = useState<string | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState<string | null>(null);
  const [auditReason, setAuditReason] = useState('');

  // Fetch shifts on mount
  useEffect(() => {
    const fetchShifts = async () => {
      setIsLoading(true);
      try {
        const data = await adminService.getShifts();
        setShifts(data);
      } catch (err) {
        alert('Lỗi khi tải ca làm việc');
      } finally {
        setIsLoading(false);
      }
    };
    fetchShifts();
  }, []);

  const handleRowClick = (shift: Shift) => {
    setEditingShiftId(shift.id);
  };

  const handleSave = async (updatedShift: Shift) => {
    if (!auditReason.trim()) {
      alert('Vui lòng nhập lý do thay đổi');
      return;
    }
    try {
      await onSave(updatedShift, auditReason);
      setShifts((prev) => prev.map((s) => (s.id === updatedShift.id ? updatedShift : s)));
      setEditingShiftId(null);
      setAuditReason('');
    } catch (err) {
      alert('Lỗi khi lưu: ' + err);
    }
  };

  const handleDeleteClick = (shiftId: string) => {
    setShowDeleteModal(shiftId);
    setAuditReason('');
  };

  const handleConfirmDelete = async () => {
    if (!auditReason.trim()) {
      alert('Vui lòng nhập lý do xóa');
      return;
    }
    try {
      await onDelete(showDeleteModal!, auditReason);
      setShifts((prev) => prev.filter((s) => s.id !== showDeleteModal));
      setShowDeleteModal(null);
      setAuditReason('');
    } catch (err) {
      alert('Lỗi khi xóa: ' + err);
    }
  };

  return (
    <div className="space-y-4">
      {/* Add Shift Button */}
      <button className="px-4 py-2 bg-emerald-600 text-white rounded-lg font-medium hover:bg-emerald-700">
        + Thêm ca làm việc
      </button>

      {/* Shift Table */}
      {isLoading ? (
        <div className="text-center py-8">Đang tải...</div>
      ) : (
        <div className="overflow-x-auto border rounded-lg">
          <table className="w-full">
            <thead className="bg-slate-100 border-b">
              <tr>
                <th className="px-4 py-2 text-left font-bold text-sm">Tên ca</th>
                <th className="px-4 py-2 text-left font-bold text-sm">Giờ bắt đầu</th>
                <th className="px-4 py-2 text-left font-bold text-sm">Giờ kết thúc</th>
                <th className="px-4 py-2 text-left font-bold text-sm">Ngưỡng đi muộn</th>
                <th className="px-4 py-2 text-left font-bold text-sm">Ngưỡng về sớm</th>
                <th className="px-4 py-2 text-left font-bold text-sm">Ngưỡng nửa ngày</th>
                <th className="px-4 py-2 text-left font-bold text-sm">OT Buffer</th>
                <th className="px-4 py-2 text-left font-bold text-sm">Hành động</th>
              </tr>
            </thead>
            <tbody>
              {shifts.map((shift) => (
                <tr
                  key={shift.id}
                  className="border-b hover:bg-slate-50 cursor-pointer"
                  onClick={() => handleRowClick(shift)}
                >
                  {editingShiftId === shift.id ? (
                    <ShiftEditRow
                      shift={shift}
                      onSave={handleSave}
                      onCancel={() => setEditingShiftId(null)}
                      auditReason={auditReason}
                      onAuditReasonChange={setAuditReason}
                    />
                  ) : (
                    <>
                      <td className="px-4 py-2">{shift.name}</td>
                      <td className="px-4 py-2">{shift.startTime}</td>
                      <td className="px-4 py-2">{shift.endTime}</td>
                      <td className="px-4 py-2">{shift.lateInThreshold}m</td>
                      <td className="px-4 py-2">{shift.earlyOutThreshold}m</td>
                      <td className="px-4 py-2">{shift.halfDayThreshold}m</td>
                      <td className="px-4 py-2">{shift.otBuffer}m</td>
                      <td className="px-4 py-2 space-x-2">
                        <button
                          onClick={() => handleRowClick(shift)}
                          className="text-blue-600 text-sm hover:underline"
                        >
                          Sửa
                        </button>
                        <button
                          onClick={() => handleDeleteClick(shift.id)}
                          className="text-red-600 text-sm hover:underline"
                        >
                          Xóa
                        </button>
                      </td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <DeleteConfirmationModal
          title="Xóa ca làm việc?"
          message="Hành động này không thể hoàn tác. Nhập lý do xóa."
          auditReason={auditReason}
          onAuditReasonChange={setAuditReason}
          onConfirm={handleConfirmDelete}
          onCancel={() => {
            setShowDeleteModal(null);
            setAuditReason('');
          }}
        />
      )}
    </div>
  );
};

interface ShiftEditRowProps {
  shift: Shift;
  onSave: (shift: Shift, auditReason: string) => Promise<void>;
  onCancel: () => void;
  auditReason: string;
  onAuditReasonChange: (reason: string) => void;
}

const ShiftEditRow: React.FC<ShiftEditRowProps> = ({
  shift,
  onSave,
  onCancel,
  auditReason,
  onAuditReasonChange,
}) => {
  const [formData, setFormData] = useState(shift);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleChange = (field: string, value: any) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = async () => {
    setIsSubmitting(true);
    try {
      await onSave(formData, auditReason);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      <td colSpan={8} className="px-4 py-4">
        <div className="bg-blue-50 border border-blue-300 rounded-lg p-4 space-y-3">
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <input
              type="text"
              value={formData.name}
              onChange={(e) => handleChange('name', e.target.value)}
              className="border rounded px-2 py-1"
              placeholder="Tên ca"
            />
            <input
              type="time"
              value={formData.startTime}
              onChange={(e) => handleChange('startTime', e.target.value)}
              className="border rounded px-2 py-1"
            />
            <input
              type="time"
              value={formData.endTime}
              onChange={(e) => handleChange('endTime', e.target.value)}
              className="border rounded px-2 py-1"
            />
            <input
              type="number"
              value={formData.lateInThreshold}
              onChange={(e) => handleChange('lateInThreshold', parseInt(e.target.value))}
              className="border rounded px-2 py-1"
              placeholder="Ngưỡng đi muộn (phút)"
            />
          </div>
          <textarea
            value={auditReason}
            onChange={(e) => onAuditReasonChange(e.target.value)}
            placeholder="Lý do thay đổi (bắt buộc)"
            className="w-full border border-red-300 bg-red-50 rounded px-3 py-2 text-sm"
            rows={2}
          />
          <div className="flex gap-2">
            <button
              onClick={handleSubmit}
              disabled={!auditReason.trim() || isSubmitting}
              className="px-4 py-2 bg-blue-600 text-white rounded font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              {isSubmitting ? 'Đang lưu...' : 'Lưu'}
            </button>
            <button
              onClick={onCancel}
              disabled={isSubmitting}
              className="px-4 py-2 border border-slate-300 rounded font-medium hover:bg-slate-50"
            >
              Hủy
            </button>
          </div>
        </div>
      </td>
    </>
  );
};

interface DeleteConfirmationModalProps {
  title: string;
  message: string;
  auditReason: string;
  onAuditReasonChange: (reason: string) => void;
  onConfirm: () => Promise<void>;
  onCancel: () => void;
}

const DeleteConfirmationModal: React.FC<DeleteConfirmationModalProps> = ({
  title,
  message,
  auditReason,
  onAuditReasonChange,
  onConfirm,
  onCancel,
}) => {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleConfirm = async () => {
    setIsSubmitting(true);
    try {
      await onConfirm();
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg max-w-md w-full p-6 space-y-4">
        <h2 className="text-lg font-bold">{title}</h2>
        <p className="text-slate-700">{message}</p>
        <textarea
          value={auditReason}
          onChange={(e) => onAuditReasonChange(e.target.value)}
          placeholder="Lý do xóa (bắt buộc)"
          className="w-full border border-red-300 bg-red-50 rounded px-3 py-2 text-sm"
          rows={3}
        />
        <div className="flex gap-3">
          <button
            onClick={handleConfirm}
            disabled={!auditReason.trim() || isSubmitting}
            className="flex-1 px-4 py-2 bg-red-600 text-white rounded font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {isSubmitting ? '...' : 'Xác nhận xóa'}
          </button>
          <button
            onClick={onCancel}
            disabled={isSubmitting}
            className="flex-1 px-4 py-2 border border-slate-300 rounded font-medium hover:bg-slate-50"
          >
            Hủy
          </button>
        </div>
      </div>
    </div>
  );
};
```

---

### 2.3 IP Manager Grid

**Tabs:** Company IPs | Individual IPs  
**Action:** Double-click to edit; Add new IP form  
**Validation:** Valid IPv4 or IPv6 format  
**Delete:** Confirmation + audit reason

**Similar structure to ShiftConfigGrid, but for Public IP addresses. (Pivot từ MAC Wi-Fi — xem ADR-002.)**

**Component:**
```tsx
// Renamed MacManagerGrid → IpManagerGrid (ADR-002)
interface IpManagerGridProps {
  scope: 'COMPANY' | 'INDIVIDUAL';
  onSave: (ip: ValidIp, auditReason: string) => Promise<void>;
  onDelete: (ipId: string, auditReason: string) => Promise<void>;
}

export const IpManagerGrid: React.FC<IpManagerGridProps> = ({
  scope,
  onSave,
  onDelete,
}) => {
  const [ips, setIps] = useState<ValidIp[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [editingIpId, setEditingIpId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    const fetchIps = async () => {
      setIsLoading(true);
      try {
        const data = await adminService.getValidIps(scope);
        setIps(data);
      } catch (err) {
        alert('Lỗi khi tải IP');
      } finally {
        setIsLoading(false);
      }
    };
    fetchIps();
  }, [scope]);

  const handleAddIp = async (newIp: ValidIp, auditReason: string) => {
    try {
      await onSave(newIp, auditReason);
      setIps((prev) => [...prev, newIp]);
      setShowForm(false);
    } catch (err) {
      alert('Lỗi khi thêm IP');
    }
  };

  const handleDeleteIp = async (ipId: string, auditReason: string) => {
    try {
      await onDelete(ipId, auditReason);
      setIps((prev) => prev.filter((ip) => ip.id !== ipId));
    } catch (err) {
      alert('Lỗi khi xóa IP');
    }
  };

  return (
    <div className="space-y-4">
      <button
        onClick={() => setShowForm(!showForm)}
        className="px-4 py-2 bg-emerald-600 text-white rounded-lg font-medium hover:bg-emerald-700"
      >
        + Thêm IP
      </button>

      {showForm && (
        <IpAddForm
          scope={scope}
          onSave={handleAddIp}
          onCancel={() => setShowForm(false)}
        />
      )}

      {isLoading ? (
        <div className="text-center py-8">Đang tải...</div>
      ) : (
        <div className="overflow-x-auto border rounded-lg">
          <table className="w-full">
            <thead className="bg-slate-100 border-b">
              <tr>
                <th className="px-4 py-2 text-left font-bold text-sm">Địa chỉ IP</th>
                <th className="px-4 py-2 text-left font-bold text-sm">Phạm vi</th>
                {scope === 'INDIVIDUAL' && <th className="px-4 py-2 text-left font-bold text-sm">Nhân viên</th>}
                <th className="px-4 py-2 text-left font-bold text-sm">Ghi chú</th>
                <th className="px-4 py-2 text-left font-bold text-sm">Được thêm</th>
                <th className="px-4 py-2 text-left font-bold text-sm">Hành động</th>
              </tr>
            </thead>
            <tbody>
              {ips.map((ip) => (
                <tr key={ip.id} className="border-b hover:bg-slate-50">
                  <td className="px-4 py-2 font-mono text-sm">{ip.ipAddress}</td>
                  <td className="px-4 py-2 text-sm">{ip.scope}</td>
                  {scope === 'INDIVIDUAL' && (
                    <td className="px-4 py-2 text-sm">{ip.employee?.name}</td>
                  )}
                  <td className="px-4 py-2 text-sm">{ip.description}</td>
                  <td className="px-4 py-2 text-sm text-slate-600">
                    {formatDistanceToNow(ip.createdAt)}
                  </td>
                  <td className="px-4 py-2 text-sm space-x-2">
                    <button className="text-blue-600 hover:underline">Sửa</button>
                    <button
                      onClick={() =>
                        handleDeleteIp(ip.id, prompt('Lý do xóa?') || '')
                      }
                      className="text-red-600 hover:underline"
                    >
                      Xóa
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

interface IpAddFormProps {
  scope: 'COMPANY' | 'INDIVIDUAL';
  onSave: (ip: ValidIp, auditReason: string) => Promise<void>;
  onCancel: () => void;
}

const IpAddForm: React.FC<IpAddFormProps> = ({ scope, onSave, onCancel }) => {
  const [ipAddress, setIpAddress] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [description, setDescription] = useState('');
  const [auditReason, setAuditReason] = useState('');
  const [employees, setEmployees] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Validate IPv4 or IPv6 format
  const isValidIp = /^(\d{1,3}\.){3}\d{1,3}$/.test(ipAddress) ||
    /^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$/.test(ipAddress);

  const handleSubmit = async () => {
    if (!isValidIp) {
      setError('Định dạng IP không hợp lệ (ví dụ: 203.0.113.45)');
      return;
    }
    if (scope === 'INDIVIDUAL' && !employeeId) {
      setError('Vui lòng chọn nhân viên');
      return;
    }
    if (!auditReason.trim()) {
      setError('Vui lòng nhập lý do thêm');
      return;
    }

    setIsSubmitting(true);
    try {
      const newIp: ValidIp = {
        id: '',
        ipAddress,
        scope,
        employeeId: scope === 'INDIVIDUAL' ? employeeId : undefined,
        description,
        createdBy: '',
        createdAt: new Date(),
      };
      await onSave(newIp, auditReason);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="bg-emerald-50 border border-emerald-300 rounded-lg p-4 space-y-3">
      <input
        type="text"
        value={ipAddress}
        onChange={(e) => setIpAddress(e.target.value)}
        placeholder="Public IP Address (ví dụ: 203.0.113.45)"
        className="w-full border rounded px-3 py-2 font-mono"
      />

      {scope === 'INDIVIDUAL' && (
        <select
          value={employeeId}
          onChange={(e) => setEmployeeId(e.target.value)}
          className="w-full border rounded px-3 py-2"
        >
          <option value="">Chọn nhân viên</option>
          {employees.map((emp) => (
            <option key={emp.id} value={emp.id}>
              {emp.name}
            </option>
          ))}
        </select>
      )}

      <input
        type="text"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="Ghi chú (tùy chọn)"
        className="w-full border rounded px-3 py-2"
      />

      <textarea
        value={auditReason}
        onChange={(e) => setAuditReason(e.target.value)}
        placeholder="Lý do thêm (bắt buộc)"
        className="w-full border border-red-300 bg-red-50 rounded px-3 py-2 text-sm"
        rows={2}
      />

      {error && <p className="text-red-600 text-sm">{error}</p>}

      <div className="flex gap-2">
        <button
          onClick={handleSubmit}
          disabled={!isValidIp || !auditReason.trim() || isSubmitting}
          className="flex-1 px-4 py-2 bg-emerald-600 text-white rounded font-medium hover:bg-emerald-700 disabled:opacity-50"
        >
          {isSubmitting ? 'Đang lưu...' : 'Lưu'}
        </button>
        <button
          onClick={onCancel}
          className="flex-1 px-4 py-2 border border-slate-300 rounded font-medium hover:bg-slate-50"
        >
          Hủy
        </button>
      </div>
    </div>
  );
};
```

---

### 2.4 Holiday Manager Grid

**Tabs:** Fixed Holidays | Dynamic Holidays  
**Action:** Add/Edit/Delete holidays  
**Date picker:** For dynamic holidays  
**Audit reason:** On every change

Similar structure to MAC Manager.

---

### 2.5 Attendance Override Form

**Purpose:** Admin can manually override any attendance record  
**Access:** Full control — change times, status, photo, everything  
**Required:** Audit reason (bắt buộc, không được rỗng)

**Component:**
```tsx
interface AttendanceOverrideFormProps {
  employeeId: string;
  date: Date;
  onSave: (changes: OverrideChanges, reason: string) => Promise<void>;
  onCancel: () => void;
}

export const AttendanceOverrideForm: React.FC<AttendanceOverrideFormProps> = ({
  employeeId,
  date,
  onSave,
  onCancel,
}) => {
  const [record, setRecord] = useState<AttendanceRecord | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [auditReason, setAuditReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const fetchRecord = async () => {
      setIsLoading(true);
      try {
        const data = await adminService.getAttendanceRecord(employeeId, date);
        setRecord(data);
      } catch (err) {
        alert('Không tìm thấy bản ghi');
      } finally {
        setIsLoading(false);
      }
    };
    fetchRecord();
  }, [employeeId, date]);

  const handleSubmit = async () => {
    if (!auditReason.trim()) {
      alert('Vui lòng nhập lý do');
      return;
    }
    if (!record) return;

    setIsSubmitting(true);
    try {
      const changes = {
        checkInTime: record.checkInTime,
        checkOutTime: record.checkOutTime,
        attendanceStatus: record.attendanceStatus,
      };
      await onSave(changes, auditReason);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) return <div>Đang tải...</div>;
  if (!record) return <div>Không tìm thấy bản ghi</div>;

  return (
    <div className="space-y-4 max-w-2xl">
      <div className="bg-slate-50 p-4 rounded-lg">
        <h3 className="font-bold mb-2">{record.employee?.name}</h3>
        <p className="text-sm">{formatDate(record.date)}</p>
      </div>

      <div className="space-y-3">
        <div>
          <label className="block text-sm font-medium mb-1">Check-in Time</label>
          <input
            type="datetime-local"
            value={record.checkInTime}
            onChange={(e) => setRecord((prev) => prev ? { ...prev, checkInTime: e.target.value } : null)}
            className="w-full border rounded px-3 py-2"
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Check-out Time</label>
          <input
            type="datetime-local"
            value={record.checkOutTime || ''}
            onChange={(e) => setRecord((prev) => prev ? { ...prev, checkOutTime: e.target.value } : null)}
            className="w-full border rounded px-3 py-2"
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Status</label>
          <select
            value={record.attendanceStatus}
            onChange={(e) => setRecord((prev) => prev ? { ...prev, attendanceStatus: e.target.value as any } : null)}
            className="w-full border rounded px-3 py-2"
          >
            <option>ON_TIME</option>
            <option>LATE_IN</option>
            <option>EARLY_OUT</option>
            <option>HALF_DAY</option>
            <option>INCOMPLETE</option>
            <option>ABSENT</option>
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Audit Reason (bắt buộc)</label>
          <textarea
            value={auditReason}
            onChange={(e) => setAuditReason(e.target.value)}
            placeholder="Lý do override..."
            className="w-full border border-red-300 bg-red-50 rounded px-3 py-2"
            rows={3}
          />
        </div>
      </div>

      <div className="flex gap-2">
        <button
          onClick={handleSubmit}
          disabled={!auditReason.trim() || isSubmitting}
          className="flex-1 px-4 py-2 bg-red-600 text-white rounded font-medium hover:bg-red-700 disabled:opacity-50"
        >
          {isSubmitting ? '...' : 'Lưu Override'}
        </button>
        <button
          onClick={onCancel}
          className="flex-1 px-4 py-2 border border-slate-300 rounded font-medium hover:bg-slate-50"
        >
          Hủy
        </button>
      </div>
    </div>
  );
};
```

---

### 2.6 Audit Log Viewer

**Purpose:** Review all admin changes (immutable log)  
**Filters:**
- Date range picker
- Admin user (dropdown)
- Target table (shifts / macs / attendance_records / etc.)
- Employee ID (search)

**Columns:**
- Timestamp
- Admin who made change
- Entity/table affected
- Field changed
- Old value → New value
- Reason

**Component:**
```tsx
interface AuditLogViewerProps {
  filters?: AuditLogFilter;
  onFilterChange: (filter: AuditLogFilter) => void;
}

export const AuditLogViewer: React.FC<AuditLogViewerProps> = ({
  filters = {},
  onFilterChange,
}) => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [currentFilters, setCurrentFilters] = useState(filters);

  useEffect(() => {
    const fetchLogs = async () => {
      setIsLoading(true);
      try {
        const data = await adminService.getAuditLogs(currentFilters);
        setLogs(data);
      } catch (err) {
        alert('Lỗi khi tải audit log');
      } finally {
        setIsLoading(false);
      }
    };
    fetchLogs();
  }, [currentFilters]);

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="bg-slate-50 p-4 rounded-lg space-y-3">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <input
            type="date"
            value={currentFilters.dateFrom || ''}
            onChange={(e) =>
              setCurrentFilters({ ...currentFilters, dateFrom: e.target.value })
            }
            className="border rounded px-3 py-2"
            placeholder="Từ ngày"
          />
          <input
            type="date"
            value={currentFilters.dateTo || ''}
            onChange={(e) =>
              setCurrentFilters({ ...currentFilters, dateTo: e.target.value })
            }
            className="border rounded px-3 py-2"
            placeholder="Đến ngày"
          />
          <select
            value={currentFilters.targetTable || ''}
            onChange={(e) =>
              setCurrentFilters({ ...currentFilters, targetTable: e.target.value })
            }
            className="border rounded px-3 py-2"
          >
            <option value="">Tất cả bảng</option>
            <option value="shifts">Shifts</option>
            <option value="valid_ips">IPs</option>
            <option value="attendance_records">Attendance</option>
            <option value="holidays">Holidays</option>
          </select>
          <input
            type="text"
            value={currentFilters.adminId || ''}
            onChange={(e) =>
              setCurrentFilters({ ...currentFilters, adminId: e.target.value })
            }
            className="border rounded px-3 py-2"
            placeholder="Admin ID"
          />
        </div>
      </div>

      {/* Logs Table */}
      {isLoading ? (
        <div className="text-center py-8">Đang tải...</div>
      ) : (
        <div className="overflow-x-auto border rounded-lg">
          <table className="w-full text-sm">
            <thead className="bg-slate-100 border-b">
              <tr>
                <th className="px-3 py-2 text-left font-bold">Thời gian</th>
                <th className="px-3 py-2 text-left font-bold">Admin</th>
                <th className="px-3 py-2 text-left font-bold">Bảng/Entity</th>
                <th className="px-3 py-2 text-left font-bold">Field</th>
                <th className="px-3 py-2 text-left font-bold">Giá trị cũ</th>
                <th className="px-3 py-2 text-left font-bold">Giá trị mới</th>
                <th className="px-3 py-2 text-left font-bold">Lý do</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id} className="border-b hover:bg-slate-50">
                  <td className="px-3 py-2">{formatDateTime(log.createdAt)}</td>
                  <td className="px-3 py-2">{log.admin?.name}</td>
                  <td className="px-3 py-2">{log.targetTable}</td>
                  <td className="px-3 py-2 font-mono text-xs">{log.fieldChanged}</td>
                  <td className="px-3 py-2 font-mono text-xs bg-red-50">{log.oldValue}</td>
                  <td className="px-3 py-2 font-mono text-xs bg-green-50">{log.newValue}</td>
                  <td className="px-3 py-2">{log.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {logs.length === 0 && !isLoading && (
        <div className="text-center py-8 text-slate-600">Không có log</div>
      )}
    </div>
  );
};

interface AuditLog {
  id: string;
  createdAt: Date;
  admin?: { id: string; name: string };
  targetTable: string;
  fieldChanged: string;
  oldValue: string;
  newValue: string;
  reason: string;
}

interface AuditLogFilter {
  dateFrom?: string;
  dateTo?: string;
  targetTable?: string;
  adminId?: string;
}
```

---

## 3. Admin Dashboard Page Layout

```tsx
// pages/Admin/ConfigPage.tsx

export const AdminConfigPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'shifts' | 'ips' | 'holidays'>('shifts');

  return (
    <div className="flex h-screen bg-slate-50">
      <AdminSidebar activeTab={activeTab} onTabChange={setActiveTab} />

      <div className="flex-1 overflow-y-auto">
        <AdminHeader />

        <main className="p-6">
          {activeTab === 'shifts' && <ShiftConfigGrid {...shiftProps} />}
          {activeTab === 'ips' && <IpManagerGrid {...ipProps} />}
          {activeTab === 'holidays' && <HolidayManagerGrid {...holidayProps} />}
        </main>
      </div>
    </div>
  );
};
```

---

## 4. Performance & Data Handling

- **Table pagination:** 50 rows per page minimum
- **Lazy loading:** Load more button or infinite scroll for large datasets
- **Caching:** Cache shift/IP/holiday data with 5-minute TTL
- **Optimistic updates:** Update UI immediately, then sync with backend
- **Bulk operations:** Allow multi-select for batch delete (future enhancement)

---

## 5. Accessibility

- **Keyboard navigation:** Tab through table rows, Enter to edit
- **Screen reader:** All buttons labeled with `aria-label`
- **Form validation:** Clear error messages, focus on first error field
- **Audit reason:** Always visible, marked as required with `*`
