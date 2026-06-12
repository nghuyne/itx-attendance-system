import React, { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { requestService } from '../../services/requestService';
import { useUiStore } from '../../store/uiStore';
import type { AttendanceRecordDto } from '../../types/api';

const createAdjustmentSchema = (checkInTime: string) => z.object({
  proposedCheckoutTime: z.string()
    .min(1, 'Vui lòng chọn thời gian checkout')
    .refine((val) => !isNaN(new Date(val).getTime()), 'Định dạng thời gian không hợp lệ')
    .refine((val) => new Date(val).getTime() > new Date(checkInTime).getTime(), 'Phải sau thời gian check-in'),
  reason: z.string().min(10, 'Lý do phải có ít nhất 10 ký tự').max(500, 'Lý do không được vượt quá 500 ký tự'),
});

type AdjustmentRequestFormValues = z.infer<ReturnType<typeof createAdjustmentSchema>>;

interface AdjustmentRequestFormProps {
  record: AttendanceRecordDto;
  onClose: () => void;
  onSuccess: () => void;
}

export const AdjustmentRequestForm: React.FC<AdjustmentRequestFormProps> = ({ record, onClose, onSuccess }) => {
  const showToast = useUiStore((s) => s.showToast);
  const schema = createAdjustmentSchema(record.checkInTime || '');

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement;
    return () => {
      previouslyFocused?.focus();
    };
  }, []);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<AdjustmentRequestFormValues>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: (data: AdjustmentRequestFormValues) =>
      requestService.submitAdjustmentRequest({
        attendanceRecordId: record.id,
        proposedCheckoutTime: new Date(data.proposedCheckoutTime).toISOString(),
        reason: data.reason,
      }),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Yêu cầu điều chỉnh được gửi thành công' });
      onSuccess();
      onClose();
    },
    onError: (err: unknown) => {
      const getErrorCode = (error: unknown): string | undefined => {
        if (error && typeof error === 'object') {
          const axiosError = error as { response?: { data?: { error?: string } } };
          return axiosError.response?.data?.error;
        }
        return undefined;
      };
      const errorCode = getErrorCode(err);
      if (errorCode === 'PENDING_REQUEST_EXISTS') {
        showToast({ type: 'error', message: 'Đã có yêu cầu điều chỉnh chưa xử lý cho bản ghi này' });
      } else if (errorCode === 'INVALID_CHECKOUT_TIME') {
        showToast({ type: 'error', message: 'Thời gian checkout phải sau thời gian check-in' });
      } else if (errorCode === 'INVALID_ATTENDANCE_STATUS') {
        showToast({ type: 'error', message: 'Chỉ có thể gửi yêu cầu cho bản ghi THIẾU' });
      } else if (errorCode === 'SECURITY_CONTEXT_MISSING') {
        showToast({ type: 'error', message: 'Phiên làm việc hết hạn, vui lòng đăng nhập lại' });
      } else if (errorCode === 'TOO_MANY_PENDING_REQUESTS') {
        showToast({ type: 'error', message: 'Bạn đã có quá nhiều yêu cầu chưa xử lý' });
      } else {
        showToast({ type: 'error', message: 'Có lỗi xảy ra, vui lòng thử lại' });
      }
    },
  });

  const onSubmit = handleSubmit((data) => {
    mutation.mutate(data);
  });

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="adjustment-form-title"
    >
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 id="adjustment-form-title" className="text-xl font-bold text-slate-700">Gửi yêu cầu điều chỉnh</h2>
          <button
            onClick={onClose}
            aria-label="Đóng"
            className="p-2 rounded-full hover:bg-slate-100 min-w-[48px] min-h-[48px] flex items-center justify-center"
          >
            ✕
          </button>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label htmlFor="proposedCheckoutTime" className="block text-sm font-medium text-slate-700 mb-1">
              Thời gian checkout đề xuất <span className="text-red-500">*</span>
            </label>
            <input
              id="proposedCheckoutTime"
              type="datetime-local"
              min={record.checkInTime ? new Date(record.checkInTime).toLocaleString('sv').slice(0, 16) : undefined}
              {...register('proposedCheckoutTime')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.proposedCheckoutTime && <p className="text-red-500 text-xs mt-1">{errors.proposedCheckoutTime.message}</p>}
            {record.checkInTime && (
              <p className="text-xs text-slate-500 mt-1">
                Check-in: {new Date(record.checkInTime).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="reason" className="block text-sm font-medium text-slate-700 mb-1">
              Lý do <span className="text-red-500">*</span>
            </label>
            <textarea
              id="reason"
              {...register('reason')}
              placeholder="Nhập lý do chi tiết (tối thiểu 10 ký tự)"
              rows={4}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 resize-none"
            />
            {errors.reason && <p className="text-red-500 text-xs mt-1">{errors.reason.message}</p>}
          </div>

          <div className="flex gap-3 justify-end">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 min-h-[48px] text-sm rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={isSubmitting || mutation.isPending}
              className="px-4 py-2 min-h-[48px] text-sm rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting || mutation.isPending ? 'Đang gửi...' : 'Gửi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
