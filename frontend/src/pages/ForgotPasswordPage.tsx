import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';
import { authService } from '../services/authService';
import { LoadingSpinner } from '../components/common/LoadingSpinner';

const schema = z.object({
  email: z.string().email('Email không hợp lệ'),
});

type FormValues = z.infer<typeof schema>;

export const ForgotPasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const [submitted, setSubmitted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (data) => {
    setIsLoading(true);
    try {
      await authService.forgotPassword(data.email);
      setSubmitted(true);
    } finally {
      setIsLoading(false);
    }
  });

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <div className="bg-white max-w-sm w-full rounded-lg shadow-md p-8">
        <h1 className="text-2xl font-bold text-primary text-center mb-2">ITX Attendance</h1>
        <h2 className="text-base font-medium text-neutral text-center mb-6">Quên mật khẩu</h2>

        {submitted ? (
          <div className="text-center">
            <p className="text-sm text-neutral mb-6">
              Nếu email tồn tại trong hệ thống, bạn sẽ nhận được hướng dẫn qua email.
            </p>
            <button
              type="button"
              onClick={() => navigate('/login')}
              className="text-sm text-primary hover:underline"
            >
              Quay lại đăng nhập
            </button>
          </div>
        ) : (
          <form onSubmit={onSubmit} noValidate>
            <div className="mb-6">
              <label htmlFor="email" className="block text-sm font-medium text-neutral mb-1">
                Email
              </label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                aria-describedby={errors.email ? 'email-error' : undefined}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                {...register('email')}
              />
              {errors.email && (
                <p id="email-error" role="alert" className="mt-1 text-xs text-danger">
                  {errors.email.message}
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
                  <span>Đang gửi...</span>
                </>
              ) : (
                'Gửi hướng dẫn'
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
        )}
      </div>
    </div>
  );
};
