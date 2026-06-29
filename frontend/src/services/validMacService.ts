import api from './api';
import type { ValidMacDto, CreateValidMacRequest } from '../types/api';

export const validMacService = {
  getAll: (): Promise<ValidMacDto[]> =>
    api.get<ValidMacDto[]>('/admin/valid-macs').then(r => r.data),

  create: (data: CreateValidMacRequest): Promise<ValidMacDto> =>
    api.post<ValidMacDto>('/admin/valid-macs', data).then(r => r.data),

  delete: (id: number): Promise<void> =>
    api.delete(`/admin/valid-macs/${id}`).then(() => {}),
};
