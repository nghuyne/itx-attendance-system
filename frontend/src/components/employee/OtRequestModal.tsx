import { useEffect, useRef } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { requestService } from '../../services/requestService';
import { useUiStore } from '../../store/uiStore';

interface OtRequestModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const getToday = () => new Date().toISOString().split('T')[0];

const schema = z.object({
  plannedDate: z.string().min(1, 'Vui lòng chọn ngày OT').refine(
    d => !d || d >= getToday(),
    'Ngày OT phải từ hôm nay trở đi'
  ),
  plannedOtHours: z
    .number({ error: 'Vui lòng nhập số giờ' })
    .min(0.5, 'Tối thiểu 0.5 giờ')
    .max(8, 'Tối đa 8 giờ'),
  reason: z.string().min(10, 'Lý do tối thiểu 10 ký tự'),
});

type FormData = z.infer<typeof schema>;

export const OtRequestModal = ({ isOpen, onClose, onSuccess }: OtRequestModalProps) => {
  const showToast = useUiStore(s => s.showToast);
  const queryClient = useQueryClient();
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isValid },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { plannedDate: '', plannedOtHours: 1, reason: '' },
    mode: 'onChange',
  });

  const mutation = useMutation({
    mutationFn: (data: FormData) =>
      requestService.submitOtRequest({
        plannedDate: data.plannedDate,
        plannedOtHours: data.plannedOtHours,
        reason: data.reason,
      }),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Đã gửi yêu cầu OT' });
      queryClient.invalidateQueries({ queryKey: ['myRequests'] });
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
        aria-labelledby="ot-modal-title"
        className="bg-white rounded-xl shadow-xl w-full max-w-md max-h-[90vh] overflow-y-auto"
      >
        <div className="p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h2 id="ot-modal-title" className="text-lg font-semibold text-neutral">
              Xin làm thêm giờ (OT)
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
              <label className="block text-sm font-medium text-neutral mb-1">Ngày làm OT</label>
              <input
                type="date"
                {...register('plannedDate')}
                min={getToday()}
                className="w-full border border-base-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
              />
              {errors.plannedDate && (
                <p className="text-xs text-red-500 mt-1">{errors.plannedDate.message}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-neutral mb-1">
                Số giờ dự kiến (0.5 – 8 giờ)
              </label>
              <input
                type="number"
                step="0.5"
                min="0.5"
                max="8"
                {...register('plannedOtHours', { valueAsNumber: true })}
                className="w-full border border-base-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
              />
              {errors.plannedOtHours && (
                <p className="text-xs text-red-500 mt-1">{errors.plannedOtHours.message}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-neutral mb-1">Lý do</label>
              <textarea
                {...register('reason')}
                rows={3}
                placeholder="Nhập lý do làm OT (tối thiểu 10 ký tự)..."
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
              {mutation.isPending ? 'Đang gửi...' : 'Gửi yêu cầu OT'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};
