import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { departmentService } from '../../services/departmentService';
import { shiftService } from '../../services/shiftService';
import type { DepartmentDto, EmployeeWithDeptDto } from '../../types/api';
import { useUiStore } from '../../store/uiStore';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { SkeletonCard } from '../../components/common/SkeletonCard';

const deptSchema = z.object({
  name: z.string().min(1, 'Vui lòng nhập tên phòng ban').max(100),
  description: z.string().optional(),
});

// ── Department form modal ────────────────────────────────────────────────────

interface DeptFormModalProps {
  editing: DepartmentDto | null;
  onClose: () => void;
  onSuccess: () => void;
}

const DeptFormModal: React.FC<DeptFormModalProps> = ({ editing, onClose, onSuccess }) => {
  const showToast = useUiStore(s => s.showToast);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(deptSchema),
    defaultValues: { name: editing?.name ?? '', description: editing?.description ?? '' },
  });

  const onSubmit = handleSubmit(async data => {
    try {
      if (editing) {
        await departmentService.update(editing.id, data);
        showToast({ type: 'success', message: 'Cập nhật phòng ban thành công' });
      } else {
        await departmentService.create(data);
        showToast({ type: 'success', message: 'Tạo phòng ban thành công' });
      }
      onSuccess();
      onClose();
    } catch (err) {
      const code = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      if (code === 'DEPARTMENT_ALREADY_EXISTS') {
        showToast({ type: 'error', message: 'Tên phòng ban đã tồn tại' });
      } else {
        showToast({ type: 'error', message: 'Có lỗi xảy ra, vui lòng thử lại' });
      }
    }
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-slate-700">
            {editing ? 'Chỉnh sửa phòng ban' : 'Tạo phòng ban mới'}
          </h2>
          <button onClick={onClose} aria-label="Đóng"
            className="p-2 rounded-full hover:bg-slate-100 min-w-[48px] min-h-[48px] flex items-center justify-center">
            &#x2715;
          </button>
        </div>
        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label htmlFor="dept-name" className="block text-sm font-medium text-slate-700 mb-1">
              Tên phòng ban <span className="text-red-500">*</span>
            </label>
            <input id="dept-name" type="text" {...register('name')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:border-transparent"
              placeholder="VD: Kỹ thuật, Kinh doanh" />
            {errors.name && <p className="text-red-500 text-xs mt-1">{errors.name.message}</p>}
          </div>
          <div>
            <label htmlFor="dept-desc" className="block text-sm font-medium text-slate-700 mb-1">
              Mô tả
            </label>
            <textarea id="dept-desc" rows={3} {...register('description')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:border-transparent resize-none"
              placeholder="Mô tả ngắn về phòng ban" />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="px-4 py-2 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 min-h-[48px]">
              Hủy
            </button>
            <button type="submit" disabled={isSubmitting}
              className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 min-h-[48px] flex items-center gap-2">
              {isSubmitting && <LoadingSpinner size="sm" />}
              {editing ? 'Lưu thay đổi' : 'Tạo phòng ban'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ── Bulk shift assign modal ──────────────────────────────────────────────────

interface BulkAssignModalProps {
  dept: DepartmentDto;
  onClose: () => void;
  onSuccess: () => void;
}

const BulkAssignModal: React.FC<BulkAssignModalProps> = ({ dept, onClose, onSuccess }) => {
  const showToast = useUiStore(s => s.showToast);
  const queryClient = useQueryClient();
  const [selectedShiftId, setSelectedShiftId] = useState('');

  const { data: shiftsPage, isLoading: isLoadingShifts } = useQuery({
    queryKey: ['admin', 'shifts'],
    queryFn: () => shiftService.getAll(0, 100),
  });

  const assignMutation = useMutation({
    mutationFn: () => departmentService.assignShift(dept.id, selectedShiftId),
    onSuccess: result => {
      showToast({ type: 'success', message: `Đã gán ca cho ${result.updatedCount} nhân viên` });
      queryClient.invalidateQueries({ queryKey: ['admin', 'shifts'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'employees-details'] });
      onSuccess();
      onClose();
    },
    onError: (err: unknown) => {
      const code = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      if (code === 'DEPARTMENT_EMPTY') {
        showToast({ type: 'error', message: 'Phòng ban không có nhân viên để gán ca' });
      } else if (code === 'SHIFT_NOT_FOUND') {
        showToast({ type: 'error', message: 'Ca làm việc không tồn tại' });
      } else {
        showToast({ type: 'error', message: 'Gán ca thất bại, vui lòng thử lại' });
      }
    },
  });

  const shifts = shiftsPage?.content ?? [];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-sm mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-slate-700">Gán ca hàng loạt</h2>
          <button onClick={onClose} aria-label="Đóng"
            className="p-2 rounded-full hover:bg-slate-100 min-w-[48px] min-h-[48px] flex items-center justify-center">
            &#x2715;
          </button>
        </div>
        <p className="text-sm text-slate-500 mb-4">
          Phòng ban: <span className="font-semibold text-slate-700">{dept.name}</span>{' '}
          ({dept.employeeCount} nhân viên)
        </p>
        <div className="mb-4">
          <label htmlFor="shiftSelect" className="block text-sm font-medium text-slate-700 mb-1">
            Chọn ca <span className="text-red-500">*</span>
          </label>
          {isLoadingShifts ? (
            <div className="flex items-center gap-2 text-sm text-slate-500 py-2">
              <LoadingSpinner size="sm" /> Đang tải...
            </div>
          ) : (
            <select id="shiftSelect" value={selectedShiftId}
              onChange={e => setSelectedShiftId(e.target.value)}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 bg-white">
              <option value="">-- Chọn ca --</option>
              {shifts.map(s => (
                <option key={s.id} value={s.id}>{s.name} ({s.startTime}–{s.endTime})</option>
              ))}
            </select>
          )}
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose}
            className="px-4 py-2 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 min-h-[48px]">
            Hủy
          </button>
          <button type="button"
            disabled={!selectedShiftId || assignMutation.isPending}
            onClick={() => assignMutation.mutate()}
            className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 min-h-[48px] flex items-center gap-2">
            {assignMutation.isPending && <LoadingSpinner size="sm" />}
            Gán ca
          </button>
        </div>
      </div>
    </div>
  );
};

// ── Delete department confirm ────────────────────────────────────────────────

interface DeleteDeptConfirmProps {
  dept: DepartmentDto;
  onConfirm: () => void;
  onCancel: () => void;
  isDeleting: boolean;
}

const DeleteDeptConfirm: React.FC<DeleteDeptConfirmProps> = ({ dept, onConfirm, onCancel, isDeleting }) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <div className="bg-white rounded-lg shadow-xl w-full max-w-sm mx-4 p-6">
      <h3 className="text-lg font-bold text-slate-700 mb-2">Xác nhận xóa phòng ban</h3>
      <p className="text-slate-600 text-sm mb-4">
        Bạn có chắc muốn xóa phòng ban <strong>"{dept.name}"</strong>? Thao tác này không thể hoàn tác.
      </p>
      <div className="flex justify-end gap-3">
        <button onClick={onCancel}
          className="px-4 py-2 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 min-h-[48px]">
          Hủy
        </button>
        <button onClick={onConfirm} disabled={isDeleting}
          className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 min-h-[48px] flex items-center gap-2">
          {isDeleting && <LoadingSpinner size="sm" />}
          Xóa
        </button>
      </div>
    </div>
  </div>
);

// ── Change employee department modal ─────────────────────────────────────────

interface ChangeDeptModalProps {
  employee: EmployeeWithDeptDto;
  departments: DepartmentDto[];
  onClose: () => void;
  onSuccess: () => void;
}

const ChangeDeptModal: React.FC<ChangeDeptModalProps> = ({ employee, departments, onClose, onSuccess }) => {
  const showToast = useUiStore(s => s.showToast);
  const queryClient = useQueryClient();
  const [selectedDeptId, setSelectedDeptId] = useState<string>(
    employee.departmentId !== null ? String(employee.departmentId) : ''
  );

  const assignMutation = useMutation({
    mutationFn: () =>
      departmentService.assignEmployeeDepartment(
        employee.id,
        selectedDeptId === '' ? null : Number(selectedDeptId)
      ),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Cập nhật phòng ban thành công' });
      queryClient.invalidateQueries({ queryKey: ['admin', 'employees-details'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'departments'] });
      onSuccess();
      onClose();
    },
    onError: () => {
      showToast({ type: 'error', message: 'Cập nhật phòng ban thất bại, vui lòng thử lại' });
    },
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-sm mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-slate-700">Đổi phòng ban</h2>
          <button onClick={onClose} aria-label="Đóng"
            className="p-2 rounded-full hover:bg-slate-100 min-w-[48px] min-h-[48px] flex items-center justify-center">
            &#x2715;
          </button>
        </div>
        <p className="text-sm text-slate-500 mb-4">
          Nhân viên: <span className="font-semibold text-slate-700">{employee.fullName}</span>
        </p>
        <div className="mb-4">
          <label htmlFor="deptSelect" className="block text-sm font-medium text-slate-700 mb-1">
            Phòng ban
          </label>
          <select id="deptSelect" value={selectedDeptId}
            onChange={e => setSelectedDeptId(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 bg-white">
            <option value="">Không có phòng ban</option>
            {departments.map(d => (
              <option key={d.id} value={String(d.id)}>{d.name}</option>
            ))}
          </select>
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose}
            className="px-4 py-2 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 min-h-[48px]">
            Hủy
          </button>
          <button type="button"
            disabled={assignMutation.isPending}
            onClick={() => assignMutation.mutate()}
            className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 min-h-[48px] flex items-center gap-2">
            {assignMutation.isPending && <LoadingSpinner size="sm" />}
            Lưu
          </button>
        </div>
      </div>
    </div>
  );
};

// ── Main page ────────────────────────────────────────────────────────────────

export const DepartmentsPage: React.FC = () => {
  const showToast = useUiStore(s => s.showToast);
  const queryClient = useQueryClient();

  const [deptModal, setDeptModal] = useState<'create' | 'edit' | null>(null);
  const [editingDept, setEditingDept] = useState<DepartmentDto | null>(null);
  const [deletingDept, setDeletingDept] = useState<DepartmentDto | null>(null);
  const [assigningDept, setAssigningDept] = useState<DepartmentDto | null>(null);
  const [changingEmpDept, setChangingEmpDept] = useState<EmployeeWithDeptDto | null>(null);

  const { data: departments, isLoading: isLoadingDepts, isError: isErrorDepts } = useQuery({
    queryKey: ['admin', 'departments'],
    queryFn: departmentService.getAll,
  });

  const { data: employees, isLoading: isLoadingEmps, isError: isErrorEmps } = useQuery({
    queryKey: ['admin', 'employees-details'],
    queryFn: departmentService.getEmployeesWithDept,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => departmentService.delete(id),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Xóa phòng ban thành công' });
      setDeletingDept(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'departments'] });
    },
    onError: (err: unknown) => {
      const code = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      if (code === 'DEPARTMENT_HAS_EMPLOYEES') {
        showToast({ type: 'error', message: 'Phòng ban còn nhân viên, không thể xóa' });
      } else {
        showToast({ type: 'error', message: 'Xóa phòng ban thất bại, vui lòng thử lại' });
      }
      setDeletingDept(null);
    },
  });

  const deptList = departments ?? [];
  const empList = employees ?? [];

  return (
    <main className="p-4 space-y-8">
      {/* ── Section 1: Departments ── */}
      <section>
        <div className="flex items-center justify-between mb-4">
          <h1 className="text-2xl font-bold text-slate-700">Phòng ban</h1>
          <button
            onClick={() => { setEditingDept(null); setDeptModal('create'); }}
            className="bg-emerald-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-emerald-700 min-h-[48px]">
            + Tạo phòng ban
          </button>
        </div>

        {isLoadingDepts ? (
          <div className="space-y-3">{[1, 2, 3].map(i => <SkeletonCard key={i} />)}</div>
        ) : isErrorDepts ? (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
            Không thể tải danh sách phòng ban. Vui lòng thử lại.
          </div>
        ) : deptList.length === 0 ? (
          <div className="text-center py-12 text-slate-500">
            <p className="text-lg">Chưa có phòng ban nào</p>
            <p className="text-sm mt-1">Bấm "Tạo phòng ban" để bắt đầu</p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-sm">
              <thead className="bg-slate-50">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Tên</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Mô tả</th>
                  <th className="text-right px-4 py-3 font-medium text-slate-600">Số nhân viên</th>
                  <th className="text-center px-4 py-3 font-medium text-slate-600">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {deptList.map(dept => (
                  <tr key={dept.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-700">{dept.name}</td>
                    <td className="px-4 py-3 text-slate-500">{dept.description ?? '—'}</td>
                    <td className="px-4 py-3 text-right">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                        dept.employeeCount > 0 ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'
                      }`}>
                        {dept.employeeCount} NV
                      </span>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <div className="flex items-center justify-center gap-2">
                        <button
                          onClick={() => setAssigningDept(dept)}
                          title="Gán ca hàng loạt"
                          className="px-2 py-1 text-xs text-emerald-700 border border-emerald-300 rounded hover:bg-emerald-50 min-h-[36px]">
                          Gán ca
                        </button>
                        <button
                          onClick={() => { setEditingDept(dept); setDeptModal('edit'); }}
                          title="Chỉnh sửa"
                          aria-label={`Sửa ${dept.name}`}
                          className="p-1 text-slate-400 hover:text-emerald-600 min-w-[36px] min-h-[36px] flex items-center justify-center">
                          &#x270F;&#xFE0F;
                        </button>
                        <button
                          onClick={() => setDeletingDept(dept)}
                          title="Xóa"
                          aria-label={`Xóa ${dept.name}`}
                          className="p-1 text-slate-400 hover:text-red-600 min-w-[36px] min-h-[36px] flex items-center justify-center">
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
      </section>

      {/* ── Section 2: Employees ── */}
      <section>
        <h2 className="text-xl font-bold text-slate-700 mb-4">Nhân viên</h2>

        {isLoadingEmps ? (
          <div className="space-y-3">{[1, 2, 3].map(i => <SkeletonCard key={i} />)}</div>
        ) : isErrorEmps ? (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
            Không thể tải danh sách nhân viên. Vui lòng thử lại.
          </div>
        ) : empList.length === 0 ? (
          <div className="text-center py-8 text-slate-500 text-sm">Không có nhân viên nào.</div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-sm">
              <thead className="bg-slate-50">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Họ tên</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Username</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Ca</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Phòng ban</th>
                  <th className="text-center px-4 py-3 font-medium text-slate-600">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {empList.map(emp => (
                  <tr key={emp.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-700">{emp.fullName}</td>
                    <td className="px-4 py-3 text-slate-500">{emp.username}</td>
                    <td className="px-4 py-3 text-slate-500">{emp.shiftName ?? '—'}</td>
                    <td className="px-4 py-3 text-slate-500">{emp.departmentName ?? '—'}</td>
                    <td className="px-4 py-3 text-center">
                      <button
                        onClick={() => setChangingEmpDept(emp)}
                        className="px-2 py-1 text-xs text-emerald-700 border border-emerald-300 rounded hover:bg-emerald-50 min-h-[36px]">
                        Đổi phòng ban
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* ── Modals ── */}
      {(deptModal === 'create' || deptModal === 'edit') && (
        <DeptFormModal
          editing={editingDept}
          onClose={() => { setDeptModal(null); setEditingDept(null); }}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin', 'departments'] })}
        />
      )}
      {deletingDept && (
        <DeleteDeptConfirm
          dept={deletingDept}
          onConfirm={() => deleteMutation.mutate(deletingDept.id)}
          onCancel={() => setDeletingDept(null)}
          isDeleting={deleteMutation.isPending}
        />
      )}
      {assigningDept && (
        <BulkAssignModal
          dept={assigningDept}
          onClose={() => setAssigningDept(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin', 'departments'] })}
        />
      )}
      {changingEmpDept && (
        <ChangeDeptModal
          employee={changingEmpDept}
          departments={deptList}
          onClose={() => setChangingEmpDept(null)}
          onSuccess={() => {}}
        />
      )}
    </main>
  );
};
