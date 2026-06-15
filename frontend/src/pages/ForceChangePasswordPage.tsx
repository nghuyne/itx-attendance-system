import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';
import { authService } from '../services/authService';
import { useAuthStore } from '../store/authStore';
import { useUiStore } from '../store/uiStore';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { UserRole } from '../types/domain';

const schema = z
  .object({
    oldPassword: z.string().min(1, 'Vui lòng nhập mật khẩu hiện tại'),
    newPassword: z.string().min(6, 'Mật khẩu mới phải có ít nhất 6 ký tự'),
    confirmPassword: z.string().min(1, 'Vui lòng xác nhận mật khẩu mới'),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    message: 'Mật khẩu xác nhận không khớp',
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

const ROLE_DEFAULT_ROUTES: Record<UserRole, string> = {
  [UserRole.EMPLOYEE]: '/check-in',
  [UserRole.LEADER]: '/leader/dashboard',
  [UserRole.ADMIN]: '/admin/shifts',
};

export const ForceChangePasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const clearMustChangePassword = useAuthStore((s) => s.clearMustChangePassword);
  const showToast = useUiStore((s) => s.showToast);
  const setLoading = useUiStore((s) => s.setLoading);
  const isLoading = useUiStore((s) => s.isLoading);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (data) => {
    setLoading(true);
    try {
      await authService.changePassword(data.oldPassword, data.newPassword);
      clearMustChangePassword();
      showToast({ type: 'success', message: 'Đổi mật khẩu thành công' });
      navigate(user ? ROLE_DEFAULT_ROUTES[user.role] : '/login', { replace: true });
    } catch {
      showToast({
        type: 'error',
        message: 'Mật khẩu hiện tại không đúng. Vui lòng thử lại.',
        duration: 4000,
      });
    } finally {
      setLoading(false);
    }
  });

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <div className="bg-white max-w-sm w-full rounded-lg shadow-md p-8">
        <h1 className="text-2xl font-bold text-primary text-center mb-2">ITX Attendance</h1>
        <p className="text-sm text-slate-500 text-center mb-6">
          Tài khoản của bạn cần đổi mật khẩu trước khi tiếp tục.
        </p>

        <form onSubmit={onSubmit} noValidate>
          <div className="mb-4">
            <label htmlFor="oldPassword" className="block text-sm font-medium text-neutral mb-1">
              Mật khẩu hiện tại
            </label>
            <input
              id="oldPassword"
              type="password"
              autoComplete="current-password"
              aria-describedby={errors.oldPassword ? 'old-error' : undefined}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              {...register('oldPassword')}
            />
            {errors.oldPassword && (
              <p id="old-error" role="alert" className="mt-1 text-xs text-danger">
                {errors.oldPassword.message}
              </p>
            )}
          </div>

          <div className="mb-4">
            <label htmlFor="newPassword" className="block text-sm font-medium text-neutral mb-1">
              Mật khẩu mới
            </label>
            <input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              aria-describedby={errors.newPassword ? 'new-error' : undefined}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              {...register('newPassword')}
            />
            {errors.newPassword && (
              <p id="new-error" role="alert" className="mt-1 text-xs text-danger">
                {errors.newPassword.message}
              </p>
            )}
          </div>

          <div className="mb-6">
            <label
              htmlFor="confirmPassword"
              className="block text-sm font-medium text-neutral mb-1"
            >
              Xác nhận mật khẩu mới
            </label>
            <input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              aria-describedby={errors.confirmPassword ? 'confirm-error' : undefined}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              {...register('confirmPassword')}
            />
            {errors.confirmPassword && (
              <p id="confirm-error" role="alert" className="mt-1 text-xs text-danger">
                {errors.confirmPassword.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="bg-primary text-white w-full py-3 rounded-lg font-medium flex items-center justify-center gap-2 hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
          >
            {isLoading ? (
              <>
                <LoadingSpinner size="sm" />
                <span>Đang xử lý...</span>
              </>
            ) : (
              'Đổi mật khẩu'
            )}
          </button>
        </form>
      </div>
    </div>
  );
};
