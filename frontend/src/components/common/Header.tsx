import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../../services/authService';
import { useAuthStore } from '../../store/authStore';
import { useUiStore } from '../../store/uiStore';
import { NotificationBell } from './NotificationBell';

interface HeaderProps {
  title: string;
  onMenuClick?: () => void;
}

export const Header: React.FC<HeaderProps> = ({ title, onMenuClick }) => {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const setLoading = useUiStore((s) => s.setLoading);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const handleLogout = async () => {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    setLoading(true);
    try {
      await authService.logout();
    } finally {
      clearAuth();
      setLoading(false);
      setIsLoggingOut(false);
      navigate('/login', { replace: true });
    }
  };

  return (
    <header className="h-14 bg-white border-b border-slate-200 flex items-center px-4 gap-3 shrink-0">
      {onMenuClick && (
        <button
          onClick={onMenuClick}
          aria-label="Mở menu"
          className="min-w-[48px] min-h-[48px] flex items-center justify-center rounded-lg hover:bg-slate-100 -ml-2"
        >
          ☰
        </button>
      )}

      <span className="font-semibold text-neutral flex-1 truncate">{title}</span>

      <NotificationBell />

      <span className="text-sm text-slate-500 hidden sm:block truncate max-w-[120px]">
        {user?.fullName}
      </span>

      <button
        onClick={handleLogout}
        disabled={isLoggingOut}
        aria-label="Đăng xuất"
        className="min-w-[48px] min-h-[48px] flex items-center justify-center rounded-lg hover:bg-slate-100 text-sm text-slate-600 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        ⎋
      </button>
    </header>
  );
};
