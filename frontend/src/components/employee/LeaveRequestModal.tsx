import { useEffect, useRef } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { requestService } from '../../services/requestService';
import { useUiStore } from '../../store/uiStore';
import type { LeaveType } from '../../types/api';

interface LeaveRequestModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const getToday = () => new Date().toISOString().split('T')[0];

const schema = z.object({
  leaveType: z.enum(['ANNUAL', 'SICK']),
  startDate: z.string().min(1, 'Vui lòng chọn ngày bắt đầu').refine(d => d >= getToday(), {
    message: 'Ngày bắt đầu không được trong quá khứ',
  }),
  endDate: z.string().min(1, 'Vui lòng chọn ngày kết thúc'),
  reason: z.string().min(10, 'Lý do phải có ít nhất 10 ký tự'),
}).refine(data => data.endDate >= data.startDate, {
  message: 'Ngày kết thúc phải sau hoặc bằng ngày bắt đầu',
  path: ['endDate'],
});

type FormData = z.infer<typeof schema>;

const LEAVE_TYPE_LABEL: Record<LeaveType, string> = {
  ANNUAL: 'Phép năm',
  SICK: 'Phép ốm',
};

export const LeaveRequestModal = ({ isOpen, onClose, onSuccess }: LeaveRequestModalProps) => {
  const showToast = useUiStore(s => s.showToast);
  const queryClient = useQueryClient();
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors, isValid },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { leaveType: 'ANNUAL', startDate: '', endDate: '', reason: '' },
    mode: 'onChange',
  });

  const leaveType = watch('leaveType') as LeaveType;
  const startDate = watch('startDate');
  const endDate = watch('endDate');

  const { data: balanceData } = useQuery({
    queryKey: ['leaveBalance'],
    queryFn: requestService.getLeaveBalance,
    enabled: isOpen,
  });

  const balance = balanceData?.find(b => b.leaveType === leaveType);
  const remaining = balance ? balance.totalDays - balance.usedDays : null;

  const dayCount =
    startDate && endDate && endDate >= startDate
      ? Math.max(
          0,
          Math.floor((new Date(endDate).getTime() - new Date(startDate).getTime()) / 86400000) + 1,
        )
      : 0;

  const isBalanceInsufficient = remaining !== null && dayCount > 0 && remaining < dayCount;

  const mutation = useMutation({
    mutationFn: requestService.submitLeaveRequest,
    onSuccess: () => {
      showToast({ type: 'success', message: 'Gửi đơn nghỉ phép thành công' });
      queryClient.invalidateQueries({ queryKey: ['myRequests'] });
      queryClient.invalidateQueries({ queryKey: ['leaveBalance'] });
      reset();
      onSuccess();
      onClose();
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Có lỗi xảy ra, vui lòng thử lại';
      showToast({ type: 'error', message: msg });
    },
  });

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => closeButtonRef.current?.focus(), 0);
    } else {
      reset();
    }
  }, [isOpen, reset]);

  if (!isOpen) return null;

  const onSubmit = (data: FormData) => {
    mutation.mutate(data);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50"
      onClick={e => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="leave-modal-title"
        className="bg-white rounded-xl shadow-xl w-full max-w-md max-h-[90vh] overflow-y-auto"
      >
        <div className="p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h2 id="leave-modal-title" className="text-lg font-semibold text-neutral">
              Xin nghỉ phép
            </h2>
            <button
              ref={closeButtonRef}
              onClick={onClose}
              className="text-slate-400 hover:text-slate-600 p-1 rounded"
              aria-label="Đóng"
            >
              ✕
            </button>
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-neutral mb-1">Loại phép</label>
              <select
                {...register('leaveType')}
                className="w-full border border-base-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
              >
                <option value="ANNUAL">Phép năm</option>
                <option value="SICK">Phép ốm</option>
              </select>
              {balance && (
                <p className="text-xs text-slate-500 mt-1">
                  Còn lại:{' '}
                  <span className={remaining !== null && remaining < dayCount ? 'text-red-500 font-medium' : 'font-medium'}>
                    {balance.totalDays - balance.usedDays} / {balance.totalDays} ngày
                  </span>{' '}
                  ({LEAVE_TYPE_LABEL[leaveType]})
                </p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-neutral mb-1">Ngày bắt đầu</label>
                <input
                  type="date"
                  {...register('startDate')}
                  min={getToday()}
                  className="w-full border border-base-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                />
                {errors.startDate && (
                  <p className="text-xs text-red-500 mt-1">{errors.startDate.message}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-neutral mb-1">Ngày kết thúc</label>
                <input
                  type="date"
                  {...register('endDate')}
                  min={startDate || getToday()}
                  className="w-full border border-base-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                />
                {errors.endDate && (
                  <p className="text-xs text-red-500 mt-1">{errors.endDate.message}</p>
                )}
              </div>
            </div>

            {dayCount > 0 && (
              <p className="text-sm text-slate-600">
                Số ngày nghỉ:{' '}
                <span className="font-semibold text-neutral">{dayCount} ngày</span>
                <span className="text-xs text-slate-400 ml-1">(tính theo ngày dương lịch)</span>
              </p>
            )}

            {isBalanceInsufficient && (
              <div className="bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                <p className="text-xs text-red-600">Số ngày nghỉ còn lại không đủ</p>
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-neutral mb-1">Lý do</label>
              <textarea
                {...register('reason')}
                rows={3}
                placeholder="Nhập lý do nghỉ phép (tối thiểu 10 ký tự)..."
                className="w-full border border-base-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 resize-none"
              />
              {errors.reason && (
                <p className="text-xs text-red-500 mt-1">{errors.reason.message}</p>
              )}
            </div>

            <button
              type="submit"
              disabled={!isValid || mutation.isPending}
              className="w-full min-h-[48px] bg-primary text-white rounded-lg font-medium disabled:opacity-50 hover:opacity-90 transition-opacity"
            >
              {mutation.isPending ? 'Đang gửi...' : 'Gửi đơn nghỉ phép'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};
