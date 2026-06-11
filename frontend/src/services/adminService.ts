import api from './api';
import type {
  AdminAttendanceRecordDto,
  AttendanceOverrideRequest,
  AttendanceRecordDto,
  AuditLogDto,
  EmployeeDto,
  PageResponse,
} from '../types/api';

export const adminService = {
  overrideAttendance: (id: string, request: AttendanceOverrideRequest): Promise<AttendanceRecordDto> =>
    api.put<AttendanceRecordDto>(`/admin/attendance/${id}/override`, request).then(r => r.data),

  searchAttendance: (
    from: string,
    to: string,
    page = 0,
    size = 20,
    employeeId?: string
  ): Promise<PageResponse<AdminAttendanceRecordDto>> =>
    api.get<PageResponse<AdminAttendanceRecordDto>>('/admin/attendance', {
      params: { from, to, page, size, ...(employeeId ? { employeeId } : {}) },
    }).then(r => r.data),

  getAuditLogs: (
    from: string,
    to: string,
    page = 0,
    size = 50,
    adminId?: string,
    targetTable?: string
  ): Promise<PageResponse<AuditLogDto>> =>
    api.get<PageResponse<AuditLogDto>>('/admin/audit-logs', {
      params: {
        from,
        to,
        page,
        size,
        ...(adminId ? { adminId } : {}),
        ...(targetTable ? { targetTable } : {}),
      },
    }).then(r => r.data),

  getAdmins: (): Promise<EmployeeDto[]> =>
    api.get<EmployeeDto[]>('/admin/admins').then(r => r.data),
};
