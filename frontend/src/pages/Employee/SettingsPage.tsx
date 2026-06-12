import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../../services/authService';
import { useAuthStore } from '../../store/authStore';
import { useUiStore } from '../../store/uiStore';

const ROLE_LABEL: Record<string, string> = {
  EMPLOYEE: 'Nhân viên',
  LEADER: 'Trưởng nhóm',
  ADMIN: 'Quản trị viên',
};

export const SettingsPage: React.FC = () => {
  const navigate = useNavigate();
  const clearAuth = useAuthStore(s => s.clearAuth);
  const user = useAuthStore(s => s.user);
  const showToast = useUiStore(s => s.showToast);
  const [isLoading, setIsLoading] = useState(false);

  const handleLogout = async () => {
    setIsLoading(true);
    try {
      await authService.logout();
      clearAuth();
      showToast({ type: 'success', message: 'Đã đăng xuất thành công' });
      navigate('/login');
    } catch (_err) {
      showToast({ type: 'error', message: 'Lỗi khi đăng xuất. Vui lòng thử lại.' });
    } finally {
      setIsLoading(false);
    }
  };

  const initials = user?.fullName
    ? user.fullName.split(' ').slice(-2).map(w => w[0]).join('').toUpperCase()
    : '?';

  return (
    <main className="p-4 space-y-4">
      <h1 className="text-2xl font-bold text-neutral">Cài đặt</h1>

      {/* Profile card */}
      <div className="bg-base-100 rounded-2xl shadow-sm border border-base-200 p-5 flex items-center gap-4">
        <div className="w-14 h-14 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
          <span className="text-xl font-bold text-primary">{initials}</span>
        </div>
        <div className="min-w-0">
          <p className="text-base font-semibold text-neutral truncate">{user?.fullName ?? '—'}</p>
          <p className="text-sm text-slate-500 truncate">@{user?.username ?? '—'}</p>
          <span className="inline-block mt-1 text-xs font-medium px-2 py-0.5 rounded-full bg-primary/10 text-primary">
            {user?.role ? (ROLE_LABEL[user.role] ?? user.role) : '—'}
          </span>
        </div>
      </div>

      {/* Menu section */}
      <div className="bg-base-100 rounded-2xl shadow-sm border border-base-200 overflow-hidden">
        <button
          onClick={handleLogout}
          disabled={isLoading}
          className="w-full flex items-center gap-3 px-5 py-4 text-left hover:bg-base-200 active:bg-base-300 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <div className="w-9 h-9 rounded-full bg-red-100 flex items-center justify-center shrink-0">
            <svg className="w-4 h-4 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h6a2 2 0 012 2v1" />
            </svg>
          </div>
          <span className="flex-1 text-sm font-medium text-red-500">
            {isLoading ? 'Đang đăng xuất...' : 'Đăng xuất'}
          </span>
          {!isLoading && (
            <svg className="w-4 h-4 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          )}
        </button>
      </div>
    </main>
  );
};
