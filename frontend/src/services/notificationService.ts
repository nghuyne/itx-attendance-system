import api from './api';
import type { NotificationDto, NotificationPendingResponse } from '../types/api';

export const notificationService = {
  getPending(): Promise<NotificationPendingResponse> {
    return api.get('/notifications/pending').then((r) => r.data);
  },

  markAsRead(id: string): Promise<NotificationDto> {
    return api.put(`/notifications/${id}/read`).then((r) => r.data);
  },

  markAllAsRead(): Promise<{ updatedCount: number }> {
    return api.put('/notifications/read-all').then((r) => r.data);
  },
};
