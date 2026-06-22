import api from './api';
import type {
  AdminAttendanceRecordDto,
  AttendanceOverrideRequest,
  AttendanceRecordDto,
  AuditLogDto,
  EmployeeDto,
  PageResponse,
} from '../types/api';
import type { AttendanceStatus } from '../types/domain';

export const adminService = {
  overrideAttendance: (id: string, request: AttendanceOverrideRequest): Promise<AttendanceRecordDto> =>
    api.put<AttendanceRecordDto>(`/admin/attendance/${id}/override`, request).then(r => r.data),

  searchAttendance: (
    from: string,
    to: string,
    page = 0,
    size = 20,
    employeeId?: string,
    statuses?: AttendanceStatus[]
  ): Promise<PageResponse<AdminAttendanceRecordDto>> => {
    const params = new URLSearchParams();
    params.set('from', from);
    params.set('to', to);
    params.set('page', String(page));
    params.set('size', String(size));
    if (employeeId) params.set('employeeId', employeeId);
    if (statuses && statuses.length > 0) {
      statuses.forEach((s) => params.append('status', s));
    }
    return api.get<PageResponse<AdminAttendanceRecordDto>>('/admin/attendance', { params }).then(r => r.data);
  },

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

  getEmployees: (): Promise<EmployeeDto[]> =>
    api.get<EmployeeDto[]>('/admin/employees').then(r => r.data),
};
