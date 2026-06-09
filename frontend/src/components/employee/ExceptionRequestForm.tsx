import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { requestService } from '../../services/requestService';
import { useUiStore } from '../../store/uiStore';
import type { ExceptionRequestType } from '../../types/api';
import type { AttendanceRecordDto } from '../../types/api';

const exceptionRequestSchema = z.object({
  requestType: z.enum(['LATE_IN', 'EARLY_OUT', 'HALF_DAY', 'LATE_IN_EARLY_OUT'] as const),
  reason: z.string().min(10, 'Lý do phải có ít nhất 10 ký tự').max(500, 'Lý do không được vượt quá 500 ký tự'),
});

type ExceptionRequestFormValues = z.infer<typeof exceptionRequestSchema>;

interface ExceptionRequestFormProps {
  record: AttendanceRecordDto;
  onClose: () => void;
  onSuccess: () => void;
}

export const ExceptionRequestForm: React.FC<ExceptionRequestFormProps> = ({ record, onClose, onSuccess }) => {
  const showToast = useUiStore((s) => s.showToast);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ExceptionRequestFormValues>({
    resolver: zodResolver(exceptionRequestSchema),
  });

  const mutation = useMutation({
    mutationFn: (data: ExceptionRequestFormValues) =>
      requestService.submitExceptionRequest({
        attendanceRecordId: record.id,
        requestType: data.requestType as ExceptionRequestType,
        reason: data.reason,
      }),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Yêu cầu ngoại lệ được gửi thành công' });
      onSuccess();
      onClose();
    },
    onError: (err: unknown) => {
      const errorCode = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      if (errorCode === 'PENDING_REQUEST_EXISTS') {
        showToast({ type: 'error', message: 'Đã có yêu cầu ngoại lệ chưa xử lý cho bản ghi này' });
      } else if (errorCode === 'INVALID_ATTENDANCE_STATUS') {
        showToast({ type: 'error', message: 'Không thể gửi yêu cầu cho trạng thái này' });
      } else {
        showToast({ type: 'error', message: 'Có lỗi xảy ra, vui lòng thử lại' });
      }
    },
  });

  const onSubmit = handleSubmit((data) => {
    mutation.mutate(data);
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-slate-700">Gửi yêu cầu ngoại lệ</h2>
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
            <label htmlFor="requestType" className="block text-sm font-medium text-slate-700 mb-1">
              Loại yêu cầu <span className="text-red-500">*</span>
            </label>
            <select
              id="requestType"
              {...register('requestType')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            >
              <option value="">Chọn loại yêu cầu</option>
              <option value="LATE_IN">Đi muộn</option>
              <option value="EARLY_OUT">Về sớm</option>
              <option value="HALF_DAY">Nửa ngày</option>
              <option value="LATE_IN_EARLY_OUT">Muộn & Sớm</option>
            </select>
            {errors.requestType && <p className="text-red-500 text-xs mt-1">{errors.requestType.message}</p>}
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
