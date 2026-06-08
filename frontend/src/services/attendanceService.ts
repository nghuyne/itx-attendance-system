import api from './api';
import type { AttendanceRecordDto, CheckInRequest, CheckOutRequest, PageResponse } from '../types/api';

export const attendanceService = {
  getTodayRecord: (): Promise<AttendanceRecordDto | null> =>
    api.get<AttendanceRecordDto | null>('/attendance/today').then(r => r.data || null),

  checkIn: (data: CheckInRequest): Promise<AttendanceRecordDto> =>
    api.post<AttendanceRecordDto>('/attendance/check-in', data).then(r => r.data),

  checkOut: (data: CheckOutRequest): Promise<AttendanceRecordDto> =>
    api.post<AttendanceRecordDto>('/attendance/check-out', data).then(r => r.data),

  getHistory: (params: {
    from: string;
    to: string;
    page?: number;
    size?: number;
  }): Promise<PageResponse<AttendanceRecordDto>> =>
    api.get<PageResponse<AttendanceRecordDto>>('/attendance/history', { params }).then(r => r.data),
};
