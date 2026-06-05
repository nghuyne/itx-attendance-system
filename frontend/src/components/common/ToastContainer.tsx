import React from 'react';
import { useUiStore } from '../../store/uiStore';
import { Toast } from './Toast';

export const ToastContainer: React.FC = () => {
  const { toasts, dismissToast } = useUiStore();

  return (
    <div
      aria-label="Thông báo hệ thống"
      className="fixed top-4 z-50 flex flex-col gap-2 left-1/2 -translate-x-1/2 sm:left-auto sm:right-4 sm:translate-x-0"
    >
      {toasts.map((toast) => (
        <Toast key={toast.id} {...toast} onClose={dismissToast} />
      ))}
    </div>
  );
};
