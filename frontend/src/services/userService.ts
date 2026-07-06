import api from './api';
import type { AdminUserDto, CreateUserRequest, PageResponse } from '../types/api';

export const userService = {
  getAll: (page = 0, size = 20): Promise<PageResponse<AdminUserDto>> =>
    api.get<PageResponse<AdminUserDto>>('/admin/users', { params: { page, size } }).then(r => r.data),

  create: (data: CreateUserRequest): Promise<AdminUserDto> =>
    api.post<AdminUserDto>('/admin/users', data).then(r => r.data),

  resetPassword: (id: string): Promise<void> =>
    api.put(`/admin/users/${id}/reset-password`).then(() => {}),

  deactivate: (id: string): Promise<AdminUserDto> =>
    api.put<AdminUserDto>(`/admin/users/${id}/deactivate`).then(r => r.data),

  activate: (id: string): Promise<AdminUserDto> =>
    api.put<AdminUserDto>(`/admin/users/${id}/activate`).then(r => r.data),
};
