// Shared mock-data builders, previously copy-pasted verbatim (or near-verbatim)
// across check-in/history/exception-request and leader/dashboard/leader/requests specs.

export function makeAttendanceRecord(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    employeeId: 'emp-id',
    shiftId: 'shift-1',
    shiftName: 'Ca Sáng',
    shiftStartTime: '08:00',
    shiftEndTime: '17:00',
    date: '2026-06-30',
    checkInTime: '2026-06-30T01:10:00',
    checkInIp: '203.0.113.10',
    checkInLat: 10.77,
    checkInLng: 106.69,
    checkInPhotoUrl: null,
    checkOutTime: '2026-06-30T10:00:00',
    checkOutIp: null,
    checkOutLat: null,
    checkOutLng: null,
    checkOutPhotoUrl: null,
    attendanceStatus: 'ON_TIME',
    approvalSubStatus: null,
    isClientSite: false,
    gpsUnavailable: false,
    suspiciousLocation: false,
    isAdminOverride: false,
    version: 0,
    createdAt: '2026-06-30T01:10:00',
    ...overrides,
  };
}

export function makeExceptionRequest(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    requestCategory: 'EXCEPTION',
    employeeId: 'emp-1',
    employeeName: 'Nguyen Van A',
    attendanceRecordId: 'rec-1',
    attendanceDate: '2026-06-30',
    requestType: 'LATE_IN',
    proposedCheckoutTime: null,
    checkInTime: '2026-06-30T01:30:00',
    checkOutTime: null,
    reason: 'Lý do xin ngoại lệ hợp lý và chi tiết',
    status: 'PENDING',
    reviewedBy: null,
    reviewReason: null,
    createdAt: '2026-06-30T02:00:00',
    updatedAt: '2026-06-30T02:00:00',
    leaveType: null,
    startDate: null,
    endDate: null,
    totalDays: null,
    plannedDate: null,
    plannedOtHours: null,
    ...overrides,
  };
}

export const MOCK_EMPLOYEES = [
  { id: 'emp-1', username: 'emp1', fullName: 'Nguyen Van A' },
  { id: 'emp-2', username: 'emp2', fullName: 'Tran Thi B' },
];

export const EMPTY_ATTENDANCE_PAGE = { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 };

export const TWO_ATTENDANCE_RECORDS = [
  {
    id: 'rec-1', employeeId: 'emp-1', employeeName: 'Nguyen Van A',
    shiftId: 'shift-1', shiftName: 'Ca Sáng', date: '2026-06-15',
    checkInTime: '2026-06-15T01:05:00', checkOutTime: '2026-06-15T10:00:00',
    checkInPhotoUrl: 'https://cdn.itx.local/photos/old.jpg', checkOutPhotoUrl: null,
    attendanceStatus: 'LATE_IN', approvalSubStatus: 'APPROVED',
    isAdminOverride: false, version: 0, createdAt: '2026-06-15T01:05:00',
  },
  {
    id: 'rec-2', employeeId: 'emp-2', employeeName: 'Tran Thi B',
    shiftId: 'shift-1', shiftName: 'Ca Sáng', date: '2026-06-15',
    checkInTime: null, checkOutTime: null,
    checkInPhotoUrl: null, checkOutPhotoUrl: null,
    attendanceStatus: 'ABSENT', approvalSubStatus: 'ADMIN_OVERRIDE',
    isAdminOverride: true, version: 1, createdAt: '2026-06-15T00:00:00',
  },
];

export const TWO_ATTENDANCE_RECORDS_PAGE = {
  content: TWO_ATTENDANCE_RECORDS, totalElements: 2, totalPages: 1, size: 20, number: 0,
};
