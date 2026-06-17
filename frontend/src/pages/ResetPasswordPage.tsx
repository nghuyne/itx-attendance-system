import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { authService } from '../services/authService';
import { useUiStore } from '../store/uiStore';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import type { AxiosError } from 'axios';

const schema = z
  .object({
    newPassword: z.string().min(8, 'Tối thiểu 8 ký tự'),
    confirmPassword: z.string().min(1, 'Vui lòng xác nhận mật khẩu'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Mật khẩu xác nhận không khớp',
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

const ERROR_MESSAGES: Record<string, string> = {
  TOKEN_EXPIRED: 'Link đã hết hạn. Vui lòng yêu cầu lại.',
  TOKEN_USED: 'Link đã được sử dụng. Vui lòng yêu cầu lại.',
  TOKEN_INVALID: 'Link không hợp lệ.',
};

export const ResetPasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const showToast = useUiStore((s) => s.showToast);
  const setLoading = useUiStore((s) => s.setLoading);
  const isLoading = useUiStore((s) => s.isLoading);

  const token = searchParams.get('token');

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  if (!token) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-4">
        <div className="bg-white max-w-sm w-full rounded-lg shadow-md p-8 text-center">
          <h1 className="text-2xl font-bold text-primary mb-4">ITX Attendance</h1>
          <p className="text-sm text-danger mb-6">Link không hợp lệ.</p>
          <button
            type="button"
            onClick={() => navigate('/login')}
            className="text-sm text-primary hover:underline"
          >
            Quay lại đăng nhập
          </button>
        </div>
      </div>
    );
  }

  const onSubmit = handleSubmit(async (data) => {
    setLoading(true);
    try {
      await authService.resetPassword(token, data.newPassword);
      showToast({ type: 'success', message: 'Đặt lại mật khẩu thành công', duration: 3000 });
      navigate('/login');
    } catch (err) {
      const code = (err as AxiosError<{ error: string }>)?.response?.data?.error;
      const message = ERROR_MESSAGES[code ?? ''] ?? 'Đã xảy ra lỗi. Vui lòng thử lại.';
      showToast({ type: 'error', message, duration: 5000 });
    } finally {
      setLoading(false);
    }
  });

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <div className="bg-white max-w-sm w-full rounded-lg shadow-md p-8">
        <h1 className="text-2xl font-bold text-primary text-center mb-2">ITX Attendance</h1>
        <h2 className="text-base font-medium text-neutral text-center mb-6">Đặt lại mật khẩu</h2>

        <form onSubmit={onSubmit} noValidate>
          <div className="mb-4">
            <label htmlFor="newPassword" className="block text-sm font-medium text-neutral mb-1">
              Mật khẩu mới
            </label>
            <input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              aria-describedby={errors.newPassword ? 'new-password-error' : undefined}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              {...register('newPassword')}
            />
            {errors.newPassword && (
              <p id="new-password-error" role="alert" className="mt-1 text-xs text-danger">
                {errors.newPassword.message}
              </p>
            )}
          </div>

          <div className="mb-6">
            <label htmlFor="confirmPassword" className="block text-sm font-medium text-neutral mb-1">
              Xác nhận mật khẩu
            </label>
            <input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              aria-describedby={errors.confirmPassword ? 'confirm-password-error' : undefined}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              {...register('confirmPassword')}
            />
            {errors.confirmPassword && (
              <p id="confirm-password-error" role="alert" className="mt-1 text-xs text-danger">
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
              'Đặt lại mật khẩu'
            )}
          </button>

          <div className="mt-4 text-center">
            <button
              type="button"
              onClick={() => navigate('/login')}
              className="text-sm text-primary hover:underline"
            >
              Quay lại đăng nhập
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
