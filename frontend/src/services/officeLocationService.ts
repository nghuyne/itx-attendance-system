import api from './api';
import type { OfficeLocationDto, CreateOfficeLocationRequest } from '../types/api';

export const officeLocationService = {
  getAll: (): Promise<OfficeLocationDto[]> =>
    api.get<OfficeLocationDto[]>('/admin/office-locations').then(r => r.data),

  create: (data: CreateOfficeLocationRequest): Promise<OfficeLocationDto> =>
    api.post<OfficeLocationDto>('/admin/office-locations', data).then(r => r.data),

  update: (id: number, data: CreateOfficeLocationRequest): Promise<OfficeLocationDto> =>
    api.put<OfficeLocationDto>(`/admin/office-locations/${id}`, data).then(r => r.data),

  delete: (id: number): Promise<void> =>
    api.delete(`/admin/office-locations/${id}`).then(() => undefined),

  toggleActive: (loc: OfficeLocationDto): Promise<OfficeLocationDto> =>
    api.put<OfficeLocationDto>(`/admin/office-locations/${loc.id}`, {
      name: loc.name,
      latitude: loc.latitude,
      longitude: loc.longitude,
      radiusMeters: loc.radiusMeters,
      isActive: !loc.active,
    }).then(r => r.data),
};
