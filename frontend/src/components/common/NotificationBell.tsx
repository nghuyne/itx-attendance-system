import React from 'react';

interface NotificationBellProps {
  unreadCount?: number;
  onClick?: () => void;
}

export const NotificationBell: React.FC<NotificationBellProps> = ({ unreadCount = 0, onClick }) => (
  <button
    onClick={onClick}
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
);
