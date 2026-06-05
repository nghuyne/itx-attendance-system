import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { validIpService } from '../../services/validIpService';
import type { ValidIpDto, IpScope } from '../../types/api';
import { useUiStore } from '../../store/uiStore';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { SkeletonCard } from '../../components/common/SkeletonCard';

const isValidIpv4 = (ip: string) =>
  /^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$/.test(ip);
const isValidIpv6 = (ip: string) =>
  ip.includes(':') && /^[0-9a-fA-F:]+$/.test(ip);
const isValidIp = (ip: string) => isValidIpv4(ip) || isValidIpv6(ip);

const ipSchema = z.object({
  ipAddress: z
    .string()
    .min(1, 'Vui lòng nhập địa chỉ IP')
    .refine(isValidIp, 'Định dạng IP không hợp lệ (ví dụ: 203.0.113.45 hoặc 2001:db8::1)'),
  scope: z.enum(['COMPANY', 'INDIVIDUAL'] as const),
  employeeId: z.string().optional(),
  description: z.string().optional(),
}).refine(
  (data) => data.scope === 'COMPANY' || (!!data.employeeId && data.employeeId.length > 0),
  { message: 'Vui lòng chọn nhân viên', path: ['employeeId'] }
);

type IpFormValues = z.infer<typeof ipSchema>;

interface IpFormModalProps {
  onClose: () => void;
  onSuccess: () => void;
}

const IpFormModal: React.FC<IpFormModalProps> = ({ onClose, onSuccess }) => {
  const showToast = useUiStore((s) => s.showToast);

  const { data: employeesData } = useQuery({
    queryKey: ['admin', 'employees'],
    queryFn: () => validIpService.getEmployees(),
    staleTime: 5 * 60 * 1000,
  });

  const employees = employeesData ?? [];

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<IpFormValues>({
    resolver: zodResolver(ipSchema),
    defaultValues: { scope: 'COMPANY' },
  });

  const selectedScope = watch('scope');

  const onSubmit = handleSubmit(async (data) => {
    try {
      await validIpService.create({
        ipAddress: data.ipAddress.trim(),
        scope: data.scope as IpScope,
        employeeId: data.scope === 'INDIVIDUAL' ? data.employeeId : undefined,
        description: data.description || undefined,
      });
      showToast({ type: 'success', message: 'Thêm IP thành công' });
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
          <h2 className="text-xl font-bold text-slate-700">Thêm IP hợp lệ</h2>
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
            <label htmlFor="ipAddress" className="block text-sm font-medium text-slate-700 mb-1">
              Địa chỉ IP <span className="text-red-500">*</span>
            </label>
            <input
              id="ipAddress"
              type="text"
              {...register('ipAddress')}
              placeholder="ví dụ: 203.0.113.45"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.ipAddress && (
              <p className="text-red-500 text-xs mt-1">{errors.ipAddress.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">
              Phạm vi <span className="text-red-500">*</span>
            </label>
            <div className="flex gap-4">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  value="COMPANY"
                  {...register('scope')}
                  className="text-emerald-600"
                />
                <span className="text-sm">Toàn công ty</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  value="INDIVIDUAL"
                  {...register('scope')}
                  className="text-emerald-600"
                />
                <span className="text-sm">Cá nhân</span>
              </label>
            </div>
          </div>

          {selectedScope === 'INDIVIDUAL' && (
            <div>
              <label htmlFor="employeeId" className="block text-sm font-medium text-slate-700 mb-1">
                Nhân viên <span className="text-red-500">*</span>
              </label>
              <select
                id="employeeId"
                {...register('employeeId')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
              >
                <option value="">Chọn nhân viên...</option>
                {employees.map((emp) => (
                  <option key={emp.id} value={emp.id}>
                    {emp.fullName} ({emp.username})
                  </option>
                ))}
              </select>
              {errors.employeeId && (
                <p className="text-red-500 text-xs mt-1">{errors.employeeId.message}</p>
              )}
            </div>
          )}

          <div>
            <label htmlFor="description" className="block text-sm font-medium text-slate-700 mb-1">
              Ghi chú <span className="text-slate-400 font-normal">(tùy chọn)</span>
            </label>
            <input
              id="description"
              type="text"
              {...register('description')}
              placeholder="ví dụ: Văn phòng HCM, IP dự phòng"
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
              disabled={isSubmitting}
              className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 min-h-[48px] flex items-center gap-2"
            >
              {isSubmitting && <LoadingSpinner size="sm" />}
              Lưu IP
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

interface DeleteConfirmProps {
  ip: ValidIpDto;
  onConfirm: () => void;
  onCancel: () => void;
  isDeleting: boolean;
}

const DeleteConfirmDialog: React.FC<DeleteConfirmProps> = ({
  ip,
  onConfirm,
  onCancel,
  isDeleting,
}) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <div className="bg-white rounded-lg shadow-xl w-full max-w-sm mx-4 p-6">
      <h3 className="text-lg font-bold text-slate-700 mb-2">Xác nhận xóa IP</h3>
      <p className="text-slate-600 text-sm mb-4">
        Bạn có chắc muốn xóa IP{' '}
        <strong className="font-mono">{ip.ipAddress}</strong>
        {ip.scope === 'INDIVIDUAL' && ip.employeeName && (
          <> (nhân viên: {ip.employeeName})</>
        )}
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
          Xóa IP
        </button>
      </div>
    </div>
  </div>
);

export const IpsPage: React.FC = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [deletingIp, setDeletingIp] = useState<ValidIpDto | null>(null);
  const showToast = useUiStore((s) => s.showToast);
  const queryClient = useQueryClient();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'valid-ips'],
    queryFn: () => validIpService.getAll(0, 100),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => validIpService.delete(id),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Xóa IP thành công' });
      setDeletingIp(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'valid-ips'] });
    },
    onError: () => {
      showToast({ type: 'error', message: 'Xóa IP thất bại, vui lòng thử lại' });
      setDeletingIp(null);
    },
  });

  const handleFormSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'valid-ips'] });
  };

  const ips = data?.content ?? [];

  const formatDate = (dateStr: string) =>
    new Date(dateStr).toLocaleDateString('vi-VN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });

  return (
    <main className="p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-slate-700">Quản lý IP hợp lệ</h1>
        <button
          onClick={() => setIsModalOpen(true)}
          className="bg-emerald-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-emerald-700 min-h-[48px]"
        >
          + Thêm IP
        </button>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <SkeletonCard key={i} />)}
        </div>
      ) : isError ? (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          Không thể tải danh sách IP. Vui lòng thử lại.
        </div>
      ) : ips.length === 0 ? (
        <div className="text-center py-12 text-slate-500">
          <p className="text-lg">Chưa có IP nào được cấu hình</p>
          <p className="text-sm mt-1">Bấm "+ Thêm IP" để thêm IP văn phòng đầu tiên</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Địa chỉ IP</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Phạm vi</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Nhân viên</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Ghi chú</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Ngày tạo</th>
                <th className="text-center px-4 py-3 font-medium text-slate-600">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {ips.map((ip) => (
                <tr key={ip.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-mono text-slate-700">{ip.ipAddress}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                      ip.scope === 'COMPANY'
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-blue-100 text-blue-800'
                    }`}>
                      {ip.scope === 'COMPANY' ? 'Toàn công ty' : 'Cá nhân'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    {ip.employeeName ?? <span className="text-slate-400">—</span>}
                  </td>
                  <td className="px-4 py-3 text-slate-500">
                    {ip.description ?? <span className="text-slate-400">—</span>}
                  </td>
                  <td className="px-4 py-3 text-slate-500">
                    {formatDate(ip.createdAt)}
                  </td>
                  <td className="px-4 py-3 text-center">
                    <button
                      onClick={() => setDeletingIp(ip)}
                      aria-label={`Xóa IP ${ip.ipAddress}`}
                      className="p-1 text-slate-400 hover:text-red-600 min-w-[36px] min-h-[36px] flex items-center justify-center mx-auto"
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
        <IpFormModal
          onClose={() => setIsModalOpen(false)}
          onSuccess={handleFormSuccess}
        />
      )}
      {deletingIp && (
        <DeleteConfirmDialog
          ip={deletingIp}
          onConfirm={() => deleteMutation.mutate(deletingIp.id)}
          onCancel={() => setDeletingIp(null)}
          isDeleting={deleteMutation.isPending}
        />
      )}
    </main>
  );
};
