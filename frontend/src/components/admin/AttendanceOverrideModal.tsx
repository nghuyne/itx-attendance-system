import React, { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQueryClient } from '@tanstack/react-query';
import { AttendanceStatus } from '../../types/domain';
import type { AdminAttendanceRecordDto } from '../../types/api';
import { adminService } from '../../services/adminService';
import { useUiStore } from '../../store/uiStore';
import { LoadingSpinner } from '../common/LoadingSpinner';

const overrideSchema = z.object({
  checkInTime: z.string().optional().nullable(),
  checkOutTime: z.string().optional().nullable(),
  attendanceStatus: z.nativeEnum(AttendanceStatus).optional().nullable(),
  photoUrl: z.string().optional().nullable(),
  auditReason: z.string().min(10, 'Lý do phải có ít nhất 10 ký tự'),
});

type OverrideFormValues = z.infer<typeof overrideSchema>;

interface AttendanceOverrideModalProps {
  record: AdminAttendanceRecordDto;
  onClose: () => void;
  onSuccess: () => void;
}

const toDatetimeLocal = (isoStr: string | null): string => {
  if (!isoStr) return '';
  return isoStr.slice(0, 16);
};

export const AttendanceOverrideModal: React.FC<AttendanceOverrideModalProps> = ({
  record,
  onClose,
  onSuccess,
}) => {
  const showToast = useUiStore((s) => s.showToast);
  const queryClient = useQueryClient();

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement;
    return () => { previouslyFocused?.focus(); };
  }, []);

  useEffect(() => {
    const handle = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handle);
    return () => document.removeEventListener('keydown', handle);
  }, [onClose]);

  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm<OverrideFormValues>({
    resolver: zodResolver(overrideSchema),
    defaultValues: {
      checkInTime: toDatetimeLocal(record.checkInTime),
      checkOutTime: toDatetimeLocal(record.checkOutTime),
      attendanceStatus: record.attendanceStatus,
      photoUrl: record.checkInPhotoUrl ?? '',
      auditReason: '',
    },
  });

  const auditReason = watch('auditReason');

  const onSubmit = handleSubmit(async (data) => {
    try {
      await adminService.overrideAttendance(record.id, {
        checkInTime: data.checkInTime || null,
        checkOutTime: data.checkOutTime || null,
        attendanceStatus: data.attendanceStatus || null,
        photoUrl: data.photoUrl || null,
        auditReason: data.auditReason,
      });
      queryClient.invalidateQueries({ queryKey: ['admin', 'attendance'] });
      showToast({ type: 'success', message: 'Ghi đè bản ghi thành công' });
      onSuccess();
      onClose();
    } catch {
      showToast({ type: 'error', message: 'Có lỗi xảy ra, vui lòng thử lại' });
    }
  });

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="override-form-title"
    >
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 id="override-form-title" className="text-xl font-bold text-slate-700">
            Override Bản Ghi Chấm Công
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            className="p-2 rounded-full hover:bg-slate-100 min-w-[48px] min-h-[48px] flex items-center justify-center"
          >
            &#x2715;
          </button>
        </div>

        <p className="text-sm text-slate-500 mb-4">
          Nhân viên: <strong>{record.employeeName}</strong> — Ngày: <strong>{record.date}</strong>
        </p>

        <form onSubmit={onSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="checkInTime" className="block text-sm font-medium text-slate-700 mb-1">
                Giờ vào
              </label>
              <input
                id="checkInTime"
                type="datetime-local"
                {...register('checkInTime')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
              />
              {errors.checkInTime && <p className="text-red-500 text-xs mt-1">{errors.checkInTime.message}</p>}
            </div>
            <div>
              <label htmlFor="checkOutTime" className="block text-sm font-medium text-slate-700 mb-1">
                Giờ ra
              </label>
              <input
                id="checkOutTime"
                type="datetime-local"
                {...register('checkOutTime')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
              />
              {errors.checkOutTime && <p className="text-red-500 text-xs mt-1">{errors.checkOutTime.message}</p>}
            </div>
          </div>

          <div>
            <label htmlFor="attendanceStatus" className="block text-sm font-medium text-slate-700 mb-1">
              Trạng thái
            </label>
            <select
              id="attendanceStatus"
              {...register('attendanceStatus')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            >
              <option value="">— Giữ nguyên / Tự tính —</option>
              {Object.values(AttendanceStatus).map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="photoUrl" className="block text-sm font-medium text-slate-700 mb-1">
              Photo URL
            </label>
            <input
              id="photoUrl"
              type="text"
              {...register('photoUrl')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
              placeholder="https://..."
            />
          </div>

          <div>
            <label htmlFor="auditReason" className="block text-sm font-medium text-slate-700 mb-1">
              Lý do thay đổi <span className="text-red-500">*</span>
            </label>
            <textarea
              id="auditReason"
              {...register('auditReason')}
              rows={3}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 resize-none"
              placeholder="Nhập lý do (tối thiểu 10 ký tự)..."
            />
            {errors.auditReason && <p className="text-red-500 text-xs mt-1">{errors.auditReason.message}</p>}
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50 min-h-[48px]"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={isSubmitting || (auditReason?.length ?? 0) < 10}
              className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed min-h-[48px] flex items-center gap-2"
            >
              {isSubmitting && <LoadingSpinner size="sm" />}
              Lưu thay đổi
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
