import React, { useEffect } from 'react';
import type { NotificationDto, NotificationType } from '../../types/api';

const TYPE_ICONS: Record<NotificationType, string> = {
  EXCEPTION_REQUEST: '⚠️',
  ADJUSTMENT_REQUEST: '📝',
  REQUEST_APPROVED: '✅',
  REQUEST_REJECTED: '❌',
  INCOMPLETE_RECORD: '🔔',
};

interface NotificationPanelProps {
  isOpen: boolean;
  onClose: () => void;
  notifications: NotificationDto[];
  onMarkAsRead: (id: string) => void;
  onMarkAllAsRead: () => void;
}

export const NotificationPanel: React.FC<NotificationPanelProps> = ({
  isOpen,
  onClose,
  notifications,
  onMarkAsRead,
  onMarkAllAsRead,
}) => {
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <>
      <div
        className="fixed inset-0 z-40"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        role="dialog"
        aria-label="Bảng thông báo"
        className="fixed inset-y-0 right-0 z-50 w-80 bg-white shadow-xl flex flex-col"
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-neutral-200">
          <h2 className="text-base font-semibold text-neutral-800">Thông báo</h2>
          <div className="flex items-center gap-2">
            {unreadCount > 0 && (
              <button
                onClick={onMarkAllAsRead}
                className="text-xs text-primary hover:underline"
              >
                Đánh dấu tất cả đã đọc
              </button>
            )}
            <button
              onClick={onClose}
              aria-label="Đóng bảng thông báo"
              className="min-w-[32px] min-h-[32px] flex items-center justify-center rounded hover:bg-neutral-100 text-neutral-500"
            >
              ✕
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto">
          {notifications.length === 0 ? (
            <p className="text-center text-neutral-500 text-sm py-8">Không có thông báo mới</p>
          ) : (
            <ul>
              {notifications.map((n) => (
                <li
                  key={n.id}
                  onClick={() => !n.isRead && onMarkAsRead(n.id)}
                  className={`px-4 py-3 border-b border-neutral-100 cursor-pointer hover:bg-neutral-50 flex gap-3 ${
                    !n.isRead ? 'border-l-4 border-l-primary' : ''
                  }`}
                >
                  <span className="text-lg flex-shrink-0">{TYPE_ICONS[n.type]}</span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm text-neutral-800 line-clamp-3">{n.message}</p>
                    <p className="text-xs text-neutral-500 mt-1">
                      {new Date(n.createdAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}
                    </p>
                  </div>
                  {!n.isRead && (
                    <span className="w-2 h-2 rounded-full bg-primary flex-shrink-0 mt-1.5" />
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </>
  );
};
