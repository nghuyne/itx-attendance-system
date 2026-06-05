import api from './api';
import type { HolidayDto, CreateHolidayRequest, PageResponse } from '../types/api';

export const holidayService = {
  getAll: (year?: number, page = 0, size = 100): Promise<PageResponse<HolidayDto>> =>
    api.get<PageResponse<HolidayDto>>('/admin/holidays', {
      params: { ...(year !== undefined && { year }), page, size },
    }).then(r => r.data),

  create: (data: CreateHolidayRequest): Promise<HolidayDto> =>
    api.post<HolidayDto>('/admin/holidays', data).then(r => r.data),

  delete: (id: number): Promise<void> =>
    api.delete(`/admin/holidays/${id}`).then(() => {}),
};
