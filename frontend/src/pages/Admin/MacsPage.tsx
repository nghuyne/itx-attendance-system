import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { validMacService } from '../../services/validMacService';
import type { ValidMacDto, CreateValidMacRequest } from '../../types/api';
import { useUiStore } from '../../store/uiStore';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { SkeletonCard } from '../../components/common/SkeletonCard';

const BSSID_REGEX = /^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/;

const macSchema = z.object({
  bssid: z
    .string()
    .min(1, 'Vui lòng nhập BSSID')
    .refine(
      (v) => BSSID_REGEX.test(v.trim()),
      'Định dạng BSSID không hợp lệ (ví dụ: AA:BB:CC:DD:EE:FF)'
    ),
  description: z.string().optional(),
});

type MacFormValues = z.infer<typeof macSchema>;

interface MacFormModalProps {
  onClose: () => void;
  onSuccess: () => void;
}

const MacFormModal: React.FC<MacFormModalProps> = ({ onClose, onSuccess }) => {
  const showToast = useUiStore((s) => s.showToast);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<MacFormValues>({
    resolver: zodResolver(macSchema),
  });

  const createMutation = useMutation({
    mutationFn: (data: CreateValidMacRequest) => validMacService.create(data),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Thêm BSSID thành công' });
      onSuccess();
      onClose();
    },
    onError: (err: unknown) => {
      const apiErr = err as { response?: { data?: { error?: string } } };
      const errorCode = apiErr?.response?.data?.error;
      if (errorCode === 'DUPLICATE_MAC') {
        showToast({ type: 'error', message: 'BSSID này đã tồn tại trong hệ thống' });
      } else {
        showToast({ type: 'error', message: 'Có lỗi xảy ra, vui lòng thử lại' });
      }
    },
  });

  const onSubmit = handleSubmit((data) => {
    createMutation.mutate({
      bssid: data.bssid.trim().toUpperCase(),
      description: data.description || undefined,
    });
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-slate-700">Thêm BSSID hợp lệ</h2>
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
            <label htmlFor="bssid" className="block text-sm font-medium text-slate-700 mb-1">
              Địa chỉ BSSID <span className="text-red-500">*</span>
            </label>
            <input
              id="bssid"
              type="text"
              {...register('bssid')}
              placeholder="ví dụ: AA:BB:CC:DD:EE:FF"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.bssid && (
              <p className="text-red-500 text-xs mt-1">{errors.bssid.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="description" className="block text-sm font-medium text-slate-700 mb-1">
              Ghi chú <span className="text-slate-400 font-normal">(tùy chọn)</span>
            </label>
            <input
              id="description"
              type="text"
              {...register('description')}
              placeholder="ví dụ: Router tầng 2, AP văn phòng HCM"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
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
              disabled={createMutation.isPending}
              className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 min-h-[48px] flex items-center gap-2"
            >
              {createMutation.isPending && <LoadingSpinner size="sm" />}
              Lưu BSSID
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

interface DeleteConfirmProps {
  mac: ValidMacDto;
  onConfirm: () => void;
  onCancel: () => void;
  isDeleting: boolean;
}

const DeleteConfirmDialog: React.FC<DeleteConfirmProps> = ({
  mac,
  onConfirm,
  onCancel,
  isDeleting,
}) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <div className="bg-white rounded-lg shadow-xl w-full max-w-sm mx-4 p-6">
      <h3 className="text-lg font-bold text-slate-700 mb-2">Xác nhận xóa BSSID</h3>
      <p className="text-slate-600 text-sm mb-4">
        Bạn có chắc muốn xóa BSSID{' '}
        <strong className="font-mono">{mac.bssid}</strong>
        ? Thao tác này không thể hoàn tác.
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
          Xóa BSSID
        </button>
      </div>
    </div>
  </div>
);

export const MacsPage: React.FC = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [deletingMac, setDeletingMac] = useState<ValidMacDto | null>(null);
  const showToast = useUiStore((s) => s.showToast);
  const queryClient = useQueryClient();

  const { data: macs = [], isLoading, isError } = useQuery({
    queryKey: ['admin', 'valid-macs'],
    queryFn: () => validMacService.getAll(),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => validMacService.delete(id),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Xóa BSSID thành công' });
      setDeletingMac(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'valid-macs'] });
    },
    onError: () => {
      showToast({ type: 'error', message: 'Xóa BSSID thất bại, vui lòng thử lại' });
      setDeletingMac(null);
    },
  });

  const handleFormSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'valid-macs'] });
  };

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '—';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '—';
    return d.toLocaleDateString('vi-VN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
  };

  return (
    <main className="p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-slate-700">Quản lý MAC (BSSID)</h1>
        <button
          onClick={() => setIsModalOpen(true)}
          className="bg-emerald-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-emerald-700 min-h-[48px]"
        >
          + Thêm BSSID
        </button>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <SkeletonCard key={i} />)}
        </div>
      ) : isError ? (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          Không thể tải danh sách BSSID. Vui lòng thử lại.
        </div>
      ) : macs.length === 0 ? (
        <div className="text-center py-12 text-slate-500">
          <p className="text-lg">Chưa có BSSID nào được cấu hình</p>
          <p className="text-sm mt-1">Bấm "+ Thêm BSSID" để thêm router Wi-Fi văn phòng đầu tiên</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Địa chỉ BSSID</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Mô tả</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Tạo bởi</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Ngày tạo</th>
                <th className="text-center px-4 py-3 font-medium text-slate-600">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {macs.map((mac) => (
                <tr key={mac.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-mono text-slate-700">{mac.bssid}</td>
                  <td className="px-4 py-3 text-slate-500">
                    {mac.description ?? <span className="text-slate-400">—</span>}
                  </td>
                  <td className="px-4 py-3 text-slate-600">{mac.createdBy}</td>
                  <td className="px-4 py-3 text-slate-500">{formatDate(mac.createdAt)}</td>
                  <td className="px-4 py-3 text-center">
                    <button
                      onClick={() => setDeletingMac(mac)}
                      aria-label={`Xóa BSSID ${mac.bssid}`}
                      className="p-1 text-slate-400 hover:text-red-600 min-w-[48px] min-h-[48px] flex items-center justify-center mx-auto"
                    >
                      🗑️
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isModalOpen && (
        <MacFormModal
          onClose={() => setIsModalOpen(false)}
          onSuccess={handleFormSuccess}
        />
      )}
      {deletingMac && (
        <DeleteConfirmDialog
          mac={deletingMac}
          onConfirm={() => deleteMutation.mutate(deletingMac.id)}
          onCancel={() => setDeletingMac(null)}
          isDeleting={deleteMutation.isPending}
        />
      )}
    </main>
  );
};
