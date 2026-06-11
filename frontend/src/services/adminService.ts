import api from './api';
import type { AdminAttendanceRecordDto, AttendanceOverrideRequest, AttendanceRecordDto, PageResponse } from '../types/api';

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
};
