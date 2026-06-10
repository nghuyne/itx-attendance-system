import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notificationService } from '../../services/notificationService';
import { useUiStore } from '../../store/uiStore';
import { NotificationPanel } from './NotificationPanel';

export const NotificationBell: React.FC = () => {
  const queryClient = useQueryClient();
  const notificationPanelOpen = useUiStore((s) => s.notificationPanelOpen);
  const setNotificationPanelOpen = useUiStore((s) => s.setNotificationPanelOpen);

  const { data } = useQuery({
    queryKey: ['notifications', 'pending'],
    queryFn: notificationService.getPending,
    refetchInterval: 15000,
  });

  const markAsReadMutation = useMutation({
    mutationFn: notificationService.markAsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications', 'pending'] }),
  });

  const markAllMutation = useMutation({
    mutationFn: notificationService.markAllAsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications', 'pending'] }),
  });

  const unreadCount = data?.unreadCount ?? 0;
  const notifications = data?.notifications ?? [];

  return (
    <>
      <button
        onClick={() => setNotificationPanelOpen(!notificationPanelOpen)}
        aria-label={`Thông báo${unreadCount > 0 ? `, ${unreadCount} chưa đọc` : ''}`}
        className="relative min-w-[48px] min-h-[48px] flex items-center justify-center rounded-lg hover:bg-slate-100"
      >
        🔔
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 w-4 h-4 bg-danger text-white text-xs rounded-full flex items-center justify-center">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      <NotificationPanel
        isOpen={notificationPanelOpen}
        onClose={() => setNotificationPanelOpen(false)}
        notifications={notifications}
        onMarkAsRead={(id) => markAsReadMutation.mutate(id)}
        onMarkAllAsRead={() => markAllMutation.mutate()}
      />
    </>
  );
};
