import api from './api';
import type { ValidIpDto, CreateValidIpRequest, EmployeeDto } from '../types/api';

export interface ValidIpPage {
  content: ValidIpDto[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const validIpService = {
  getAll: (page = 0, size = 100): Promise<ValidIpPage> =>
    api.get<ValidIpPage>('/admin/valid-ips', { params: { page, size } }).then(r => r.data),

  create: (data: CreateValidIpRequest): Promise<ValidIpDto> =>
    api.post<ValidIpDto>('/admin/valid-ips', data).then(r => r.data),

  delete: (id: number): Promise<void> =>
    api.delete(`/admin/valid-ips/${id}`).then(() => {}),

  getEmployees: (): Promise<EmployeeDto[]> =>
    api.get<EmployeeDto[]>('/admin/employees').then(r => r.data),
};
