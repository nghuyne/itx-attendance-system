import React, { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { holidayService } from '../../services/holidayService';
import type { HolidayDto, HolidayType } from '../../types/api';
import { useUiStore } from '../../store/uiStore';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { SkeletonCard } from '../../components/common/SkeletonCard';

const formatDate = (dateStr: string) => {
  if (!dateStr) return '—';
  const [year, month, day] = dateStr.split('-');
  return `${day}/${month}/${year}`;
};

const holidaySchema = z.object({
  date: z
    .string()
    .min(1, 'Vui lòng chọn ngày')
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Định dạng ngày không hợp lệ'),
  name: z.string().min(2, 'Tên ngày lễ phải có ít nhất 2 ký tự').max(255),
  type: z.enum(['FIXED', 'DYNAMIC'] as const),
  year: z
    .number({ invalid_type_error: 'Năm phải là số nguyên' })
    .int()
    .min(1900, 'Năm phải ≥ 1900')
    .max(2100, 'Năm phải ≤ 2100'),
});

type HolidayFormValues = z.infer<typeof holidaySchema>;

interface HolidayFormModalProps {
  onClose: () => void;
  onSuccess: () => void;
}

const HolidayFormModal: React.FC<HolidayFormModalProps> = ({ onClose, onSuccess }) => {
  const showToast = useUiStore((s) => s.showToast);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<HolidayFormValues>({
    resolver: zodResolver(holidaySchema),
    defaultValues: { type: 'FIXED', year: new Date().getFullYear() },
  });

  const selectedDate = watch('date');
  React.useEffect(() => {
    if (selectedDate && /^\d{4}/.test(selectedDate)) {
      const yearFromDate = parseInt(selectedDate.substring(0, 4), 10);
      if (yearFromDate >= 1900 && yearFromDate <= 2100) {
        setValue('year', yearFromDate);
      }
    }
  }, [selectedDate, setValue]);

  const onSubmit = handleSubmit(async (data) => {
    try {
      await holidayService.create({
        date: data.date,
        name: data.name.trim(),
        type: data.type as HolidayType,
        year: data.year,
      });
      showToast({ type: 'success', message: 'Thêm ngày lễ thành công' });
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const errorCode = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      if (errorCode === 'HOLIDAY_DATE_EXISTS') {
        showToast({ type: 'error', message: 'Đã có ngày lễ cho ngày này' });
      } else {
        showToast({ type: 'error', message: 'Có lỗi xảy ra, vui lòng thử lại' });
      }
    }
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-slate-700">Thêm ngày lễ</h2>
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
            <label htmlFor="date" className="block text-sm font-medium text-slate-700 mb-1">
              Ngày <span className="text-red-500">*</span>
            </label>
            <input
              id="date"
              type="date"
              {...register('date')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.date && <p className="text-red-500 text-xs mt-1">{errors.date.message}</p>}
          </div>

          <div>
            <label htmlFor="name" className="block text-sm font-medium text-slate-700 mb-1">
              Tên ngày lễ <span className="text-red-500">*</span>
            </label>
            <input
              id="name"
              type="text"
              {...register('name')}
              placeholder="ví dụ: Tết Nguyên Đán"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.name && <p className="text-red-500 text-xs mt-1">{errors.name.message}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">
              Loại <span className="text-red-500">*</span>
            </label>
            <div className="flex gap-4">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="radio" value="FIXED" {...register('type')} className="text-emerald-600" />
                <span className="text-sm">Cố định (Dương lịch)</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="radio" value="DYNAMIC" {...register('type')} className="text-emerald-600" />
                <span className="text-sm">Linh hoạt (Âm lịch)</span>
              </label>
            </div>
          </div>

          <div>
            <label htmlFor="year" className="block text-sm font-medium text-slate-700 mb-1">
              Năm <span className="text-red-500">*</span>
            </label>
            <input
              id="year"
              type="number"
              {...register('year', { valueAsNumber: true })}
              min={1900}
              max={2100}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
            />
            {errors.year && <p className="text-red-500 text-xs mt-1">{errors.year.message}</p>}
            <p className="text-xs text-slate-400 mt-1">Tự động điền từ ngày được chọn</p>
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
              Lưu ngày lễ
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

interface DeleteConfirmProps {
  holiday: HolidayDto;
  onConfirm: () => void;
  onCancel: () => void;
  isDeleting: boolean;
}

const DeleteConfirmDialog: React.FC<DeleteConfirmProps> = ({
  holiday,
  onConfirm,
  onCancel,
  isDeleting,
}) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <div className="bg-white rounded-lg shadow-xl w-full max-w-sm mx-4 p-6">
      <h3 className="text-lg font-bold text-slate-700 mb-2">Xác nhận xóa ngày lễ</h3>
      <p className="text-slate-600 text-sm mb-4">
        Bạn có chắc muốn xóa{' '}
        <strong>{holiday.name}</strong> ({formatDate(holiday.date)})?
        Thao tác này không thể hoàn tác.
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

export const HolidaysPage: React.FC = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [deletingHoliday, setDeletingHoliday] = useState<HolidayDto | null>(null);
  const [typeFilter, setTypeFilter] = useState<'ALL' | 'FIXED' | 'DYNAMIC'>('ALL');
  const [yearFilter, setYearFilter] = useState<number>(new Date().getFullYear());
  const showToast = useUiStore((s) => s.showToast);
  const queryClient = useQueryClient();

  const currentYear = new Date().getFullYear();
  const yearOptions = Array.from({ length: 5 }, (_, i) => currentYear - 2 + i);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'holidays', yearFilter],
    queryFn: () => holidayService.getAll(yearFilter, 0, 100),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => holidayService.delete(id),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Xóa ngày lễ thành công' });
      setDeletingHoliday(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'holidays', yearFilter] });
    },
    onError: () => {
      showToast({ type: 'error', message: 'Xóa ngày lễ thất bại, vui lòng thử lại' });
      setDeletingHoliday(null);
    },
  });

  const handleFormSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'holidays', yearFilter] });
  };

  const allHolidays = data?.content ?? [];
  const filteredHolidays = useMemo(
    () =>
      typeFilter === 'ALL'
        ? allHolidays
        : allHolidays.filter((h) => h.type === typeFilter),
    [allHolidays, typeFilter]
  );

  const typeLabel = (type: HolidayType) =>
    type === 'FIXED' ? 'Cố định' : 'Linh hoạt';

  const typeBadgeClass = (type: HolidayType) =>
    type === 'FIXED'
      ? 'bg-blue-100 text-blue-800'
      : 'bg-amber-100 text-amber-800';

  return (
    <main className="p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-slate-700">Quản lý Ngày lễ</h1>
        <button
          onClick={() => setIsModalOpen(true)}
          className="bg-emerald-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-emerald-700 min-h-[48px]"
        >
          + Thêm ngày lễ
        </button>
      </div>

      <div className="mb-4 flex gap-3">
        <select
          value={yearFilter}
          onChange={(e) => setYearFilter(Number(e.target.value))}
          className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
          aria-label="Lọc theo năm"
        >
          {yearOptions.map((y) => (
            <option key={y} value={y}>{y}</option>
          ))}
        </select>
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value as typeof typeFilter)}
          className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
          aria-label="Lọc theo loại ngày lễ"
        >
          <option value="ALL">Tất cả loại</option>
          <option value="FIXED">Cố định (Dương lịch)</option>
          <option value="DYNAMIC">Linh hoạt (Âm lịch)</option>
        </select>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <SkeletonCard key={i} />)}
        </div>
      ) : isError ? (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          Không thể tải danh sách ngày lễ. Vui lòng thử lại.
        </div>
      ) : filteredHolidays.length === 0 ? (
        <div className="text-center py-12 text-slate-500">
          <p className="text-lg">Chưa có ngày lễ nào</p>
          <p className="text-sm mt-1">Bấm &ldquo;+ Thêm ngày lễ&rdquo; để cấu hình</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Ngày</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Tên ngày lễ</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Loại</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Năm</th>
                <th className="text-center px-4 py-3 font-medium text-slate-600">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredHolidays.map((holiday) => (
                <tr key={holiday.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-mono text-slate-700">{formatDate(holiday.date)}</td>
                  <td className="px-4 py-3 text-slate-700">{holiday.name}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${typeBadgeClass(holiday.type)}`}>
                      {typeLabel(holiday.type)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{holiday.year}</td>
                  <td className="px-4 py-3 text-center">
                    <button
                      onClick={() => setDeletingHoliday(holiday)}
                      aria-label={`Xóa ngày lễ ${holiday.name}`}
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
        <HolidayFormModal
          onClose={() => setIsModalOpen(false)}
          onSuccess={handleFormSuccess}
        />
      )}
      {deletingHoliday && (
        <DeleteConfirmDialog
          holiday={deletingHoliday}
          onConfirm={() => deleteMutation.mutate(deletingHoliday.id)}
          onCancel={() => setDeletingHoliday(null)}
          isDeleting={deleteMutation.isPending}
        />
      )}
    </main>
  );
};
