import api from './api';
import type { ShiftDto, CreateShiftRequest } from '../types/api';

export interface ShiftPage {
  content: ShiftDto[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const shiftService = {
  getAll: (page = 0, size = 20): Promise<ShiftPage> =>
    api.get<ShiftPage>('/admin/shifts', { params: { page, size } }).then(r => r.data),

  create: (data: CreateShiftRequest): Promise<ShiftDto> =>
    api.post<ShiftDto>('/admin/shifts', data).then(r => r.data),

  update: (id: string, data: CreateShiftRequest): Promise<ShiftDto> =>
    api.put<ShiftDto>(`/admin/shifts/${id}`, data).then(r => r.data),

  delete: (id: string): Promise<void> =>
    api.delete(`/admin/shifts/${id}`).then(() => {}),

  assignToEmployee: (shiftId: string, employeeId: string): Promise<ShiftDto> =>
    api.put<ShiftDto>(`/admin/shifts/${shiftId}/assign/${employeeId}`).then(r => r.data),
};
