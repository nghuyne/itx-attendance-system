import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { shiftService } from '../../services/shiftService';
import type { ShiftDto } from '../../types/api';
import { useUiStore } from '../../store/uiStore';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { SkeletonCard } from '../../components/common/SkeletonCard';

const shiftSchema = z.object({
  name: z.string().min(1, 'Vui lòng nhập tên ca'),
  startTime: z.string().min(1, 'Vui lòng nhập giờ bắt đầu'),
  endTime: z.string().min(1, 'Vui lòng nhập giờ kết thúc'),
  checkInOpenMinutes: z.coerce.number().int().min(0, 'Phải >= 0'),
  lateInThreshold: z.coerce.number().int().min(0, 'Phải >= 0'),
  earlyOutThreshold: z.coerce.number().int().min(0, 'Phải >= 0'),
  halfDayThreshold: z.coerce.number().int().min(0, 'Phải >= 0'),
  otBuffer: z.coerce.number().int().min(0, 'Phải >= 0'),
}).refine(
  (data) => data.startTime < data.endTime,
  { message: 'Giờ bắt đầu phải nhỏ hơn giờ kết thúc (ca xuyên đêm chưa được hỗ trợ)', path: ['endTime'] }
);

type ShiftFormValues = z.infer<typeof shiftSchema>;

interface ShiftFormModalProps {
  editingShift: ShiftDto | null;
  onClose: () => void;
  onSuccess: () => void;
}

const ShiftFormModal: React.FC<ShiftFormModalProps> = ({ editingShift, onClose, onSuccess }) => {
  const showToast = useUiStore((s) => s.showToast);

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<ShiftFormValues>({
    resolver: zodResolver(shiftSchema),
    defaultValues: editingShift
      ? {
          name: editingShift.name,
          startTime: editingShift.startTime,
          endTime: editingShift.endTime,
          checkInOpenMinutes: editingShift.checkInOpenMinutes,
          lateInThreshold: editingShift.lateInThreshold,
          earlyOutThreshold: editingShift.earlyOutThreshold,
          halfDayThreshold: editingShift.halfDayThreshold,
          otBuffer: editingShift.otBuffer,
        }
      : {
          checkInOpenMinutes: 30,
          lateInThreshold: 0,
          earlyOutThreshold: 0,
          halfDayThreshold: 30,
          otBuffer: 30,
        },
  });

  const onSubmit = handleSubmit(async (data) => {
    try {
      if (editingShift) {
        await shiftService.update(editingShift.id, data);
        showToast({ type: 'success', message: 'Cập nhật ca thành công' });
      } else {
        await shiftService.create(data);
        showToast({ type: 'success', message: 'Tạo ca thành công' });
      }
      onSuccess();
      onClose();
    } catch {
      showToast({ type: 'error', message: 'Có lỗi xảy ra, vui lòng thử lại' });
    }
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-slate-700">
            {editingShift ? 'Chỉnh sửa ca' : 'Tạo ca mới'}
          </h2>
          <button
            onClick={onClose}
            aria-label="Đóng"
            className="p-2 rounded-full hover:bg-slate-100 min-w-[48px] min-h-[48px] flex items-center justify-center"
          >
            &#x2715;
          </button>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label htmlFor="name" className="block text-sm font-medium text-slate-700 mb-1">
              Tên ca <span className="text-red-500">*</span>
            </label>
            <input
              id="name"
              type="text"
              {...register('name')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:border-transparent"
              placeholder="VD: Ca Sáng, Ca Chiều"
            />
            {errors.name && <p className="text-red-500 text-xs mt-1">{errors.name.message}</p>}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="startTime" className="block text-sm font-medium text-slate-700 mb-1">
                Giờ bắt đầu <span className="text-red-500">*</span>
              </label>
              <input
                id="startTime"
                type="time"
                {...register('startTime')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
              />
              {errors.startTime && <p className="text-red-500 text-xs mt-1">{errors.startTime.message}</p>}
            </div>
            <div>
              <label htmlFor="endTime" className="block text-sm font-medium text-slate-700 mb-1">
                Giờ kết thúc <span className="text-red-500">*</span>
              </label>
              <input
                id="endTime"
                type="time"
                {...register('endTime')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
              />
              {errors.endTime && <p className="text-red-500 text-xs mt-1">{errors.endTime.message}</p>}
            </div>
          </div>

          <div>
            <label htmlFor="checkInOpenMinutes" className="block text-sm font-medium text-slate-700 mb-1">
              Mở cổng check-in trước (phút)
            </label>
            <input
              id="checkInOpenMinutes"
              type="number"
              min={0}
              {...register('checkInOpenMinutes')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.checkInOpenMinutes && <p className="text-red-500 text-xs mt-1">{errors.checkInOpenMinutes.message}</p>}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="lateInThreshold" className="block text-sm font-medium text-slate-700 mb-1">
                Ngưỡng trễ (phút)
              </label>
              <input id="lateInThreshold" type="number" min={0} {...register('lateInThreshold')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600" />
              {errors.lateInThreshold && <p className="text-red-500 text-xs mt-1">{errors.lateInThreshold.message}</p>}
            </div>
            <div>
              <label htmlFor="earlyOutThreshold" className="block text-sm font-medium text-slate-700 mb-1">
                Ngưỡng về sớm (phút)
              </label>
              <input id="earlyOutThreshold" type="number" min={0} {...register('earlyOutThreshold')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600" />
              {errors.earlyOutThreshold && <p className="text-red-500 text-xs mt-1">{errors.earlyOutThreshold.message}</p>}
            </div>
            <div>
              <label htmlFor="halfDayThreshold" className="block text-sm font-medium text-slate-700 mb-1">
                Ngưỡng nửa ngày (phút)
              </label>
              <input id="halfDayThreshold" type="number" min={0} {...register('halfDayThreshold')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600" />
              {errors.halfDayThreshold && <p className="text-red-500 text-xs mt-1">{errors.halfDayThreshold.message}</p>}
            </div>
            <div>
              <label htmlFor="otBuffer" className="block text-sm font-medium text-slate-700 mb-1">
                Buffer OT (phút)
              </label>
              <input id="otBuffer" type="number" min={0} {...register('otBuffer')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600" />
              {errors.otBuffer && <p className="text-red-500 text-xs mt-1">{errors.otBuffer.message}</p>}
            </div>
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
              disabled={isSubmitting}
              className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 min-h-[48px] flex items-center gap-2"
            >
              {isSubmitting && <LoadingSpinner size="sm" />}
              {editingShift ? 'Lưu thay đổi' : 'Tạo ca'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

interface DeleteConfirmProps {
  shift: ShiftDto;
  onConfirm: () => void;
  onCancel: () => void;
  isDeleting: boolean;
}

const DeleteConfirmDialog: React.FC<DeleteConfirmProps> = ({ shift, onConfirm, onCancel, isDeleting }) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <div className="bg-white rounded-lg shadow-xl w-full max-w-sm mx-4 p-6">
      <h3 className="text-lg font-bold text-slate-700 mb-2">Xác nhận xóa ca</h3>
      <p className="text-slate-600 text-sm mb-4">
        Bạn có chắc muốn xóa ca <strong>"{shift.name}"</strong>? Thao tác này không thể hoàn tác.
      </p>
      <div className="flex justify-end gap-3">
        <button
          onClick={onCancel}
          className="px-4 py-2 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 min-h-[48px]"
        >
          Hủy
        </button>
        <button
          onClick={onConfirm}
          disabled={isDeleting}
          className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 min-h-[48px] flex items-center gap-2"
        >
          {isDeleting && <LoadingSpinner size="sm" />}
          Xóa ca
        </button>
      </div>
    </div>
  </div>
);

export const ShiftsPage: React.FC = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingShift, setEditingShift] = useState<ShiftDto | null>(null);
  const [deletingShift, setDeletingShift] = useState<ShiftDto | null>(null);
  const showToast = useUiStore((s) => s.showToast);
  const queryClient = useQueryClient();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'shifts'],
    queryFn: () => shiftService.getAll(0, 100),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => shiftService.delete(id),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Xóa ca thành công' });
      setDeletingShift(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'shifts'] });
    },
    onError: (error: unknown) => {
      const apiError = error as { response?: { data?: { error?: string } } };
      if (apiError.response?.data?.error === 'SHIFT_IN_USE') {
        showToast({ type: 'error', message: 'Ca đang được gán cho nhân viên, không thể xóa' });
      } else {
        showToast({ type: 'error', message: 'Xóa ca thất bại, vui lòng thử lại' });
      }
      setDeletingShift(null);
    },
  });

  const handleRowDoubleClick = (shift: ShiftDto) => {
    setEditingShift(shift);
    setIsModalOpen(true);
  };

  const handleCreateNew = () => {
    setEditingShift(null);
    setIsModalOpen(true);
  };

  const handleModalClose = () => {
    setIsModalOpen(false);
    setEditingShift(null);
  };

  const handleFormSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'shifts'] });
  };

  const shifts = data?.content ?? [];

  return (
    <main className="p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-slate-700">Ca làm việc</h1>
        <button
          onClick={handleCreateNew}
          className="bg-emerald-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-emerald-700 min-h-[48px]"
        >
          + Tạo ca mới
        </button>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <SkeletonCard key={i} />)}
        </div>
      ) : isError ? (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          Không thể tải danh sách ca. Vui lòng thử lại.
        </div>
      ) : shifts.length === 0 ? (
        <div className="text-center py-12 text-slate-500">
          <p className="text-lg">Chưa có ca nào</p>
          <p className="text-sm mt-1">Bấm "Tạo ca mới" để bắt đầu</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Tên ca</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Bắt đầu</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Kết thúc</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Ngưỡng trễ</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Ngưỡng về sớm</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Nửa ngày</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Buffer OT</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Nhân viên</th>
                <th className="text-center px-4 py-3 font-medium text-slate-600">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {shifts.map((shift) => (
                <tr
                  key={shift.id}
                  onDoubleClick={() => handleRowDoubleClick(shift)}
                  className="hover:bg-slate-50 cursor-pointer"
                  title="Double-click để chỉnh sửa"
                >
                  <td className="px-4 py-3 font-medium text-slate-700">{shift.name}</td>
                  <td className="px-4 py-3 font-mono text-slate-600">{shift.startTime}</td>
                  <td className="px-4 py-3 font-mono text-slate-600">{shift.endTime}</td>
                  <td className="px-4 py-3 text-right text-slate-600">{shift.lateInThreshold} phút</td>
                  <td className="px-4 py-3 text-right text-slate-600">{shift.earlyOutThreshold} phút</td>
                  <td className="px-4 py-3 text-right text-slate-600">{shift.halfDayThreshold} phút</td>
                  <td className="px-4 py-3 text-right text-slate-600">{shift.otBuffer} phút</td>
                  <td className="px-4 py-3 text-right">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                      shift.assignedCount > 0 ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'
                    }`}>
                      {shift.assignedCount} NV
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <div className="flex items-center justify-center gap-2">
                      <button
                        onClick={(e) => { e.stopPropagation(); handleRowDoubleClick(shift); }}
                        aria-label={`Chỉnh sửa ca ${shift.name}`}
                        className="p-1 text-slate-400 hover:text-emerald-600 min-w-[36px] min-h-[36px] flex items-center justify-center"
                      >
                        &#x270F;&#xFE0F;
                      </button>
                      <button
                        onClick={(e) => { e.stopPropagation(); setDeletingShift(shift); }}
                        aria-label={`Xóa ca ${shift.name}`}
                        className="p-1 text-slate-400 hover:text-red-600 min-w-[36px] min-h-[36px] flex items-center justify-center"
                      >
                        &#x1F5D1;&#xFE0F;
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isModalOpen && (
        <ShiftFormModal
          editingShift={editingShift}
          onClose={handleModalClose}
          onSuccess={handleFormSuccess}
        />
      )}
      {deletingShift && (
        <DeleteConfirmDialog
          shift={deletingShift}
          onConfirm={() => deleteMutation.mutate(deletingShift.id)}
          onCancel={() => setDeletingShift(null)}
          isDeleting={deleteMutation.isPending}
        />
      )}
    </main>
  );
};
