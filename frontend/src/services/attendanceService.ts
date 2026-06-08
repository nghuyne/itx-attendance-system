import api from './api';
import type { AttendanceRecordDto, CheckInRequest } from '../types/api';

export const attendanceService = {
  getTodayRecord: (): Promise<AttendanceRecordDto | null> =>
    api.get<AttendanceRecordDto | null>('/attendance/today').then(r => r.data),

  checkIn: (data: CheckInRequest): Promise<AttendanceRecordDto> =>
    api.post<AttendanceRecordDto>('/attendance/check-in', data).then(r => r.data),
};
