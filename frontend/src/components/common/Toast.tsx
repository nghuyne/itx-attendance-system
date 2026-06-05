import { useEffect } from 'react';
import React from 'react';
import { TOAST_COLORS } from '../../types/designTokens';
import type { ToastMessage } from '../../store/uiStore';

interface ToastProps extends ToastMessage {
  onClose: (id: string) => void;
}

export const Toast: React.FC<ToastProps> = ({ id, type, message, duration = 4000, onClose }) => {
  useEffect(() => {
    const timer = setTimeout(() => onClose(id), duration);
    return () => clearTimeout(timer);
  }, [id, duration, onClose]);

  const colors = TOAST_COLORS[type];

  return (
    <div
      role="status"
      aria-live="polite"
      aria-atomic="true"
      className={`flex items-start gap-3 p-4 rounded-lg shadow-lg border-l-4 min-w-[280px] max-w-[400px] ${colors.border} ${colors.bg}`}
    >
      <span className={`text-lg leading-none mt-0.5 ${colors.text}`} aria-hidden="true">
        {colors.icon}
      </span>
      <span className={`flex-1 text-sm ${colors.text}`}>{message}</span>
      <button
        onClick={() => onClose(id)}
        aria-label="Đóng thông báo"
        className={`${colors.text} hover:opacity-70 text-xl leading-none min-h-[48px] min-w-[48px] flex items-center justify-center`}
      >
        ×
      </button>
    </div>
  );
};
