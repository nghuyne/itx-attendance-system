import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { officeLocationService } from '../../services/officeLocationService';
import type { OfficeLocationDto } from '../../types/api';
import { useUiStore } from '../../store/uiStore';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { SkeletonCard } from '../../components/common/SkeletonCard';

const locationSchema = z.object({
  name: z.string().min(1, 'Vui lòng nhập tên vị trí'),
  latitude: z.number({ error: 'Vĩ độ không hợp lệ' }).min(-90).max(90),
  longitude: z.number({ error: 'Kinh độ không hợp lệ' }).min(-180).max(180),
  radiusMeters: z
    .number({ error: 'Bán kính không hợp lệ' })
    .int()
    .min(50, 'Tối thiểu 50m')
    .max(5000, 'Tối đa 5000m'),
});

type LocationFormValues = z.infer<typeof locationSchema>;

interface LocationFormModalProps {
  editTarget: OfficeLocationDto | null;
  onClose: () => void;
  onSuccess: () => void;
}

const LocationFormModal: React.FC<LocationFormModalProps> = ({ editTarget, onClose, onSuccess }) => {
  const showToast = useUiStore((s) => s.showToast);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LocationFormValues>({
    resolver: zodResolver(locationSchema),
    defaultValues: editTarget
      ? {
          name: editTarget.name,
          latitude: editTarget.latitude,
          longitude: editTarget.longitude,
          radiusMeters: editTarget.radiusMeters,
        }
      : { radiusMeters: 200 },
  });

  const onSubmit = handleSubmit(async (data) => {
    try {
      if (editTarget) {
        await officeLocationService.update(editTarget.id, {
          ...data,
          isActive: editTarget.active,
        });
        showToast({ type: 'success', message: 'Cập nhật vị trí thành công' });
      } else {
        await officeLocationService.create(data);
        showToast({ type: 'success', message: 'Thêm vị trí thành công' });
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
            {editTarget ? 'Sửa vị trí văn phòng' : 'Thêm vị trí văn phòng'}
          </h2>
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
            <label htmlFor="name" className="block text-sm font-medium text-slate-700 mb-1">
              Tên vị trí <span className="text-red-500">*</span>
            </label>
            <input
              id="name"
              type="text"
              {...register('name')}
              placeholder="ví dụ: Văn phòng HCM"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.name && <p className="text-red-500 text-xs mt-1">{errors.name.message}</p>}
          </div>

          <div>
            <label htmlFor="latitude" className="block text-sm font-medium text-slate-700 mb-1">
              Vĩ độ <span className="text-red-500">*</span>
            </label>
            <input
              id="latitude"
              type="number"
              step="0.000001"
              {...register('latitude', { valueAsNumber: true })}
              placeholder="ví dụ: 10.776950"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.latitude && <p className="text-red-500 text-xs mt-1">{errors.latitude.message}</p>}
          </div>

          <div>
            <label htmlFor="longitude" className="block text-sm font-medium text-slate-700 mb-1">
              Kinh độ <span className="text-red-500">*</span>
            </label>
            <input
              id="longitude"
              type="number"
              step="0.000001"
              {...register('longitude', { valueAsNumber: true })}
              placeholder="ví dụ: 106.700700"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.longitude && <p className="text-red-500 text-xs mt-1">{errors.longitude.message}</p>}
          </div>

          <div>
            <label htmlFor="radiusMeters" className="block text-sm font-medium text-slate-700 mb-1">
              Bán kính (m) <span className="text-red-500">*</span>
            </label>
            <input
              id="radiusMeters"
              type="number"
              step="1"
              {...register('radiusMeters', { valueAsNumber: true })}
              placeholder="ví dụ: 200"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.radiusMeters && (
              <p className="text-red-500 text-xs mt-1">{errors.radiusMeters.message}</p>
            )}
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
              {editTarget ? 'Cập nhật' : 'Thêm vị trí'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

interface DeleteConfirmProps {
  location: OfficeLocationDto;
  onConfirm: () => void;
  onCancel: () => void;
  isDeleting: boolean;
}

const DeleteConfirmDialog: React.FC<DeleteConfirmProps> = ({
  location,
  onConfirm,
  onCancel,
  isDeleting,
}) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <div className="bg-white rounded-lg shadow-xl w-full max-w-sm mx-4 p-6">
      <h3 className="text-lg font-bold text-slate-700 mb-2">Xác nhận xóa vị trí</h3>
      <p className="text-slate-600 text-sm mb-4">
        Bạn có chắc muốn xóa vị trí <strong>{location.name}</strong>? Thao tác này không thể hoàn tác.
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
          Xóa
        </button>
      </div>
    </div>
  </div>
);

export const OfficeLocationsPage: React.FC = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<OfficeLocationDto | null>(null);
  const [deletingLocation, setDeletingLocation] = useState<OfficeLocationDto | null>(null);
  const showToast = useUiStore((s) => s.showToast);
  const queryClient = useQueryClient();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'office-locations'],
    queryFn: () => officeLocationService.getAll(),
    staleTime: 5 * 60 * 1000,
  });

  const toggleMutation = useMutation({
    mutationFn: (loc: OfficeLocationDto) => officeLocationService.toggleActive(loc),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'office-locations'] });
    },
    onError: () => {
      showToast({ type: 'error', message: 'Cập nhật trạng thái thất bại' });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => officeLocationService.delete(id),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Xóa vị trí thành công' });
      setDeletingLocation(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'office-locations'] });
    },
    onError: () => {
      showToast({ type: 'error', message: 'Xóa vị trí thất bại, vui lòng thử lại' });
      setDeletingLocation(null);
    },
  });

  const handleFormSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'office-locations'] });
  };

  const openAddModal = () => {
    setEditTarget(null);
    setIsModalOpen(true);
  };

  const openEditModal = (loc: OfficeLocationDto) => {
    setEditTarget(loc);
    setIsModalOpen(true);
  };

  const locations = data ?? [];

  return (
    <main className="p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-slate-700">Vị trí văn phòng</h1>
        <button
          onClick={openAddModal}
          className="bg-emerald-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-emerald-700 min-h-[48px]"
        >
          + Thêm vị trí
        </button>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <SkeletonCard key={i} />)}
        </div>
      ) : isError ? (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          Không thể tải danh sách vị trí. Vui lòng thử lại.
        </div>
      ) : locations.length === 0 ? (
        <div className="text-center py-12 text-slate-500">
          <p className="text-lg">Chưa có vị trí văn phòng nào</p>
          <p className="text-sm mt-1">Bấm "+ Thêm vị trí" để thêm vị trí đầu tiên</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">STT</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Tên</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Vĩ độ</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Kinh độ</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Bán kính (m)</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Trạng thái</th>
                <th className="text-center px-4 py-3 font-medium text-slate-600">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {locations.map((loc, idx) => (
                <tr key={loc.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-500">{idx + 1}</td>
                  <td className="px-4 py-3 font-medium text-slate-700">{loc.name}</td>
                  <td className="px-4 py-3 font-mono text-slate-600">{loc.latitude}</td>
                  <td className="px-4 py-3 font-mono text-slate-600">{loc.longitude}</td>
                  <td className="px-4 py-3 text-slate-600">{loc.radiusMeters}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                        loc.active
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-slate-100 text-slate-600'
                      }`}
                    >
                      {loc.active ? 'Hoạt động' : 'Tắt'}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-center gap-1">
                      <button
                        onClick={() => openEditModal(loc)}
                        aria-label={`Sửa ${loc.name}`}
                        className="p-1 text-slate-400 hover:text-emerald-600 min-w-[36px] min-h-[36px] flex items-center justify-center"
                      >
                        ✏️
                      </button>
                      <button
                        onClick={() => toggleMutation.mutate(loc)}
                        disabled={toggleMutation.isPending}
                        aria-label={loc.active ? `Tắt ${loc.name}` : `Bật ${loc.name}`}
                        className="p-1 text-slate-400 hover:text-blue-600 min-w-[36px] min-h-[36px] flex items-center justify-center disabled:opacity-40"
                      >
                        {loc.active ? '🔴' : '🟢'}
                      </button>
                      <button
                        onClick={() => setDeletingLocation(loc)}
                        aria-label={`Xóa ${loc.name}`}
                        className="p-1 text-slate-400 hover:text-red-600 min-w-[36px] min-h-[36px] flex items-center justify-center"
                      >
                        🗑️
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
        <LocationFormModal
          editTarget={editTarget}
          onClose={() => setIsModalOpen(false)}
          onSuccess={handleFormSuccess}
        />
      )}
      {deletingLocation && (
        <DeleteConfirmDialog
          location={deletingLocation}
          onConfirm={() => deleteMutation.mutate(deletingLocation.id)}
          onCancel={() => setDeletingLocation(null)}
          isDeleting={deleteMutation.isPending}
        />
      )}
    </main>
  );
};
