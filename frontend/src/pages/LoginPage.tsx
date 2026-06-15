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

const loginSchema = z.object({
  username: z.string().min(1, 'Vui lòng nhập tên đăng nhập'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

const ROLE_DEFAULT_ROUTES: Record<UserRole, string> = {
  [UserRole.EMPLOYEE]: '/check-in',
  [UserRole.LEADER]: '/leader/dashboard',
  [UserRole.ADMIN]: '/admin/shifts',
};

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const showToast = useUiStore((s) => s.showToast);
  const setLoading = useUiStore((s) => s.setLoading);
  const isLoading = useUiStore((s) => s.isLoading);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = handleSubmit(async (data) => {
    setLoading(true);
    try {
      const response = await authService.login(data);
      setAuth(response.accessToken, response.user);
      if (response.user.mustChangePassword) {
        navigate('/change-password', { replace: true });
      } else {
        navigate(ROLE_DEFAULT_ROUTES[response.user.role], { replace: true });
      }
    } catch {
      showToast({ type: 'error', message: 'Tên đăng nhập hoặc mật khẩu không đúng', duration: 4000 });
    } finally {
      setLoading(false);
    }
  });

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <div className="bg-white max-w-sm w-full rounded-lg shadow-md p-8">
        <h1 className="text-2xl font-bold text-primary text-center mb-6">ITX Attendance</h1>

        <form onSubmit={onSubmit} noValidate>
          <div className="mb-4">
            <label htmlFor="username" className="block text-sm font-medium text-neutral mb-1">
              Tên đăng nhập
            </label>
            <input
              id="username"
              type="text"
              autoComplete="username"
              aria-describedby={errors.username ? 'username-error' : undefined}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              {...register('username')}
            />
            {errors.username && (
              <p id="username-error" role="alert" className="mt-1 text-xs text-danger">
                {errors.username.message}
              </p>
            )}
          </div>

          <div className="mb-6">
            <label htmlFor="password" className="block text-sm font-medium text-neutral mb-1">
              Mật khẩu
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              aria-describedby={errors.password ? 'password-error' : undefined}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              {...register('password')}
            />
            {errors.password && (
              <p id="password-error" role="alert" className="mt-1 text-xs text-danger">
                {errors.password.message}
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
                <span>Đang đăng nhập...</span>
              </>
            ) : (
              'Đăng nhập'
            )}
          </button>
        </form>
      </div>
    </div>
  );
};
