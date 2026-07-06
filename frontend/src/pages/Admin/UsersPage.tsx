import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { userService } from '../../services/userService';
import { departmentService } from '../../services/departmentService';
import { shiftService } from '../../services/shiftService';
import type { AdminUserDto } from '../../types/api';
import { UserRole } from '../../types/domain';
import { useUiStore } from '../../store/uiStore';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { SkeletonCard } from '../../components/common/SkeletonCard';

const ROLE_LABEL: Record<string, string> = {
  EMPLOYEE: 'Nhân viên',
  LEADER: 'Trưởng nhóm',
  ADMIN: 'Quản trị viên',
};

const createUserSchema = z.object({
  username: z.string().min(3, 'Tên đăng nhập phải từ 3-50 ký tự').max(50),
  email: z.string().email('Email không hợp lệ'),
  fullName: z.string().min(1, 'Vui lòng nhập họ tên').max(100),
  role: z.enum(UserRole),
  departmentId: z.string().optional(),
  shiftId: z.string().optional(),
});

// ── Create user modal ────────────────────────────────────────────────────────

interface CreateUserModalProps {
  onClose: () => void;
  onSuccess: () => void;
}

const CreateUserModal: React.FC<CreateUserModalProps> = ({ onClose, onSuccess }) => {
  const showToast = useUiStore(s => s.showToast);

  const { data: departments } = useQuery({
    queryKey: ['admin', 'departments'],
    queryFn: departmentService.getAll,
  });
  const { data: shiftsPage } = useQuery({
    queryKey: ['admin', 'shifts'],
    queryFn: () => shiftService.getAll(0, 100),
  });

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(createUserSchema),
    defaultValues: { role: UserRole.EMPLOYEE, departmentId: '', shiftId: '' },
  });

  const onSubmit = handleSubmit(async data => {
    try {
      await userService.create({
        username: data.username,
        email: data.email,
        fullName: data.fullName,
        role: data.role,
        departmentId: data.departmentId ? Number(data.departmentId) : null,
        shiftId: data.shiftId ? data.shiftId : null,
      });
      showToast({ type: 'success', message: 'Tạo tài khoản thành công. Mật khẩu tạm thời đã được gửi qua email.' });
      onSuccess();
      onClose();
    } catch (err) {
      const code = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      if (code === 'USERNAME_ALREADY_EXISTS') {
        showToast({ type: 'error', message: 'Tên đăng nhập đã tồn tại' });
      } else if (code === 'EMAIL_ALREADY_EXISTS') {
        showToast({ type: 'error', message: 'Email đã tồn tại' });
      } else {
        showToast({ type: 'error', message: 'Có lỗi xảy ra, vui lòng thử lại' });
      }
    }
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-slate-700">Tạo tài khoản mới</h2>
          <button onClick={onClose} aria-label="Đóng"
            className="p-2 rounded-full hover:bg-slate-100 min-w-[48px] min-h-[48px] flex items-center justify-center">
            &#x2715;
          </button>
        </div>
        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label htmlFor="user-username" className="block text-sm font-medium text-slate-700 mb-1">
              Tên đăng nhập <span className="text-red-500">*</span>
            </label>
            <input id="user-username" type="text" {...register('username')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:border-transparent"
              placeholder="VD: nguyenvana" />
            {errors.username && <p className="text-red-500 text-xs mt-1">{errors.username.message}</p>}
          </div>
          <div>
            <label htmlFor="user-email" className="block text-sm font-medium text-slate-700 mb-1">
              Email <span className="text-red-500">*</span>
            </label>
            <input id="user-email" type="email" {...register('email')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:border-transparent"
              placeholder="VD: nguyenvana@congty.com" />
            {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email.message}</p>}
          </div>
          <div>
            <label htmlFor="user-fullname" className="block text-sm font-medium text-slate-700 mb-1">
              Họ tên <span className="text-red-500">*</span>
            </label>
            <input id="user-fullname" type="text" {...register('fullName')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:border-transparent"
              placeholder="VD: Nguyễn Văn A" />
            {errors.fullName && <p className="text-red-500 text-xs mt-1">{errors.fullName.message}</p>}
          </div>
          <div>
            <label htmlFor="user-role" className="block text-sm font-medium text-slate-700 mb-1">
              Vai trò <span className="text-red-500">*</span>
            </label>
            <select id="user-role" {...register('role')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 bg-white">
              {Object.values(UserRole).map(role => (
                <option key={role} value={role}>{ROLE_LABEL[role] ?? role}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="user-department" className="block text-sm font-medium text-slate-700 mb-1">
              Phòng ban
            </label>
            <select id="user-department" {...register('departmentId')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 bg-white">
              <option value="">-- Không có --</option>
              {(departments ?? []).map(d => (
                <option key={d.id} value={String(d.id)}>{d.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="user-shift" className="block text-sm font-medium text-slate-700 mb-1">
              Ca làm việc
            </label>
            <select id="user-shift" {...register('shiftId')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600 bg-white">
              <option value="">-- Không có --</option>
              {(shiftsPage?.content ?? []).map(s => (
                <option key={s.id} value={s.id}>{s.name} ({s.startTime}–{s.endTime})</option>
              ))}
            </select>
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="px-4 py-2 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 min-h-[48px]">
              Hủy
            </button>
            <button type="submit" disabled={isSubmitting}
              className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 min-h-[48px] flex items-center gap-2">
              {isSubmitting && <LoadingSpinner size="sm" />}
              Tạo tài khoản
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ── Reset password confirm ───────────────────────────────────────────────────

interface ResetPasswordConfirmProps {
  user: AdminUserDto;
  onConfirm: () => void;
  onCancel: () => void;
  isResetting: boolean;
}

const ResetPasswordConfirm: React.FC<ResetPasswordConfirmProps> = ({ user, onConfirm, onCancel, isResetting }) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <div className="bg-white rounded-lg shadow-xl w-full max-w-sm mx-4 p-6">
      <h3 className="text-lg font-bold text-slate-700 mb-2">Đặt lại mật khẩu</h3>
      <p className="text-slate-600 text-sm mb-4">
        Đặt lại mật khẩu cho <strong>"{user.fullName}"</strong>? Mật khẩu tạm thời mới sẽ được gửi qua email của họ.
      </p>
      <div className="flex justify-end gap-3">
        <button onClick={onCancel}
          className="px-4 py-2 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 min-h-[48px]">
          Hủy
        </button>
        <button onClick={onConfirm} disabled={isResetting}
          className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 min-h-[48px] flex items-center gap-2">
          {isResetting && <LoadingSpinner size="sm" />}
          Đặt lại mật khẩu
        </button>
      </div>
    </div>
  </div>
);

// ── Main page ────────────────────────────────────────────────────────────────

export const UsersPage: React.FC = () => {
  const showToast = useUiStore(s => s.showToast);
  const queryClient = useQueryClient();

  const [page, setPage] = useState(0);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [resettingUser, setResettingUser] = useState<AdminUserDto | null>(null);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'users', page],
    queryFn: () => userService.getAll(page, 20),
  });

  const resetPasswordMutation = useMutation({
    mutationFn: (id: string) => userService.resetPassword(id),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Đã đặt lại mật khẩu, email đã được gửi' });
      setResettingUser(null);
    },
    onError: () => {
      showToast({ type: 'error', message: 'Đặt lại mật khẩu thất bại, vui lòng thử lại' });
      setResettingUser(null);
    },
  });

  const toggleActiveMutation = useMutation({
    mutationFn: (u: AdminUserDto) => (u.active ? userService.deactivate(u.id) : userService.activate(u.id)),
    onSuccess: () => {
      showToast({ type: 'success', message: 'Cập nhật trạng thái tài khoản thành công' });
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
    },
    onError: (err: unknown) => {
      const code = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      if (code === 'CANNOT_DEACTIVATE_SELF') {
        showToast({ type: 'error', message: 'Không thể tự vô hiệu hóa tài khoản của chính mình' });
      } else {
        showToast({ type: 'error', message: 'Cập nhật trạng thái thất bại, vui lòng thử lại' });
      }
    },
  });

  const users = data?.content ?? [];

  return (
    <main className="p-4 space-y-6">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-slate-700">Tài khoản nhân viên</h1>
        <button
          onClick={() => setShowCreateModal(true)}
          className="bg-emerald-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-emerald-700 min-h-[48px]">
          + Tạo tài khoản
        </button>
      </div>

      {isLoading ? (
        <div className="space-y-3">{[1, 2, 3].map(i => <SkeletonCard key={i} />)}</div>
      ) : isError ? (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          Không thể tải danh sách tài khoản. Vui lòng thử lại.
        </div>
      ) : users.length === 0 ? (
        <div className="text-center py-12 text-slate-500">
          <p className="text-lg">Chưa có tài khoản nào</p>
          <p className="text-sm mt-1">Bấm "Tạo tài khoản" để bắt đầu</p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-sm">
              <thead className="bg-slate-50">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Họ tên</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Username</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Email</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Vai trò</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Phòng ban</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Ca</th>
                  <th className="text-center px-4 py-3 font-medium text-slate-600">Trạng thái</th>
                  <th className="text-center px-4 py-3 font-medium text-slate-600">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {users.map(u => (
                  <tr key={u.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-700">{u.fullName}</td>
                    <td className="px-4 py-3 text-slate-500">{u.username}</td>
                    <td className="px-4 py-3 text-slate-500">{u.email}</td>
                    <td className="px-4 py-3 text-slate-500">{ROLE_LABEL[u.role] ?? u.role}</td>
                    <td className="px-4 py-3 text-slate-500">{u.departmentName ?? '—'}</td>
                    <td className="px-4 py-3 text-slate-500">{u.shiftName ?? '—'}</td>
                    <td className="px-4 py-3 text-center">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                        u.active ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'
                      }`}>
                        {u.active ? 'Hoạt động' : 'Vô hiệu hóa'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <div className="flex items-center justify-center gap-2">
                        <button
                          onClick={() => setResettingUser(u)}
                          title="Đặt lại mật khẩu"
                          className="px-2 py-1 text-xs text-emerald-700 border border-emerald-300 rounded hover:bg-emerald-50 min-h-[36px]">
                          Đặt lại MK
                        </button>
                        <button
                          onClick={() => toggleActiveMutation.mutate(u)}
                          disabled={toggleActiveMutation.isPending}
                          title={u.active ? 'Vô hiệu hóa' : 'Kích hoạt'}
                          className={`px-2 py-1 text-xs border rounded min-h-[36px] disabled:opacity-50 ${
                            u.active
                              ? 'text-red-700 border-red-300 hover:bg-red-50'
                              : 'text-emerald-700 border-emerald-300 hover:bg-emerald-50'
                          }`}>
                          {u.active ? 'Vô hiệu hóa' : 'Kích hoạt'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex items-center gap-2 justify-end">
            <button
              onClick={() => setPage(p => p - 1)}
              disabled={page === 0}
              className="min-h-[48px] px-3 py-1.5 rounded border text-sm disabled:opacity-50 hover:bg-slate-50">
              Trước
            </button>
            <span className="text-sm text-slate-600">
              Trang {(data?.number ?? 0) + 1}/{data?.totalPages ?? 1}
            </span>
            <button
              onClick={() => setPage(p => p + 1)}
              disabled={page >= (data?.totalPages ?? 1) - 1}
              className="min-h-[48px] px-3 py-1.5 rounded border text-sm disabled:opacity-50 hover:bg-slate-50">
              Sau
            </button>
          </div>
        </>
      )}

      {showCreateModal && (
        <CreateUserModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })}
        />
      )}
      {resettingUser && (
        <ResetPasswordConfirm
          user={resettingUser}
          onConfirm={() => resetPasswordMutation.mutate(resettingUser.id)}
          onCancel={() => setResettingUser(null)}
          isResetting={resetPasswordMutation.isPending}
        />
      )}
    </main>
  );
};
