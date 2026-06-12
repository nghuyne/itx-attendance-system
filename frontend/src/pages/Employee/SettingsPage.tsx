import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../../services/authService';
import { useAuthStore } from '../../store/authStore';
import { useUiStore } from '../../store/uiStore';

export const SettingsPage: React.FC = () => {
  const navigate = useNavigate();
  const clearAuth = useAuthStore(s => s.clearAuth);
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

  return (
    <main className="p-4">
      <h1 className="text-2xl font-bold text-neutral">Cài đặt</h1>
      <div className="mt-6">
        <button
          onClick={handleLogout}
          disabled={isLoading}
          className="px-4 py-2 min-h-[48px] bg-red-500 text-white rounded-lg font-medium hover:bg-red-600 active:scale-95 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isLoading ? 'Đang đăng xuất...' : 'Đăng xuất'}
        </button>
      </div>
    </main>
  );
};
