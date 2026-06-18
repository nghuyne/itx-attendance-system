import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { adminService } from '../../services/adminService';
import type { AdminAttendanceRecordDto } from '../../types/api';
import { AttendanceStatus, ATTENDANCE_STATUS_LABEL, ATTENDANCE_STATUS_COLORS } from '../../types/domain';
import { SkeletonCard } from '../../components/common/SkeletonCard';
import { AttendanceOverrideModal } from '../../components/admin/AttendanceOverrideModal';
import { reportService } from '../../services/reportService';
import { useUiStore } from '../../store/uiStore';

const getToday = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });

const formatVN = (isoStr: string | null): string => {
  if (!isoStr) return '—';
  return new Date(isoStr + 'Z').toLocaleTimeString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
  });
};

export const AdminAttendancePage: React.FC = () => {
  const [from, setFrom] = useState(getToday);
  const [to, setTo] = useState(getToday);
  const [page, setPage] = useState(0);
  const [employeeId, setEmployeeId] = useState<string>('');
  const [isExporting, setIsExporting] = useState(false);
  const [overridingRecord, setOverridingRecord] = useState<AdminAttendanceRecordDto | null>(null);
  const queryClient = useQueryClient();
  const showToast = useUiStore((s) => s.showToast);

  const { data: employees } = useQuery({
    queryKey: ['admin', 'employees'],
    queryFn: () => adminService.getEmployees(),
    staleTime: 10 * 60 * 1000,
  });

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'attendance', from, to, page, employeeId],
    queryFn: () => adminService.searchAttendance(from, to, page, 20, employeeId || undefined),
  });

  const records = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  const handleSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'attendance'] });
  };

  const handleExport = async () => {
    setIsExporting(true);
    try {
      await reportService.exportAttendance(from, to, employeeId || undefined);
      showToast({ type: 'success', message: 'Đang tải xuống báo cáo...' });
    } catch {
      showToast({ type: 'error', message: 'Có lỗi xảy ra khi xuất báo cáo!' });
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <main className="p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-slate-700">Chấm công</h1>
        <button
          onClick={handleExport}
          disabled={isExporting}
          className="flex items-center gap-2 px-4 py-2 bg-emerald-600 text-white text-sm font-medium rounded-lg hover:bg-emerald-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors min-h-[48px]"
        >
          {isExporting ? (
            <>
              <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Đang tải...
            </>
          ) : (
            <>
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              Xuất Excel
            </>
          )}
        </button>
      </div>
      <div className="flex flex-wrap gap-3 mb-4 p-4 bg-slate-50 rounded-lg border border-slate-200">
        <div>
          <label htmlFor="from" className="block text-xs font-medium text-slate-600 mb-1">Từ ngày</label>
          <input
            id="from"
            type="date"
            value={from}
            onChange={(e) => { setFrom(e.target.value); setPage(0); }}
            className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
          />
        </div>
        <div>
          <label htmlFor="to" className="block text-xs font-medium text-slate-600 mb-1">Đến ngày</label>
          <input
            id="to"
            type="date"
            value={to}
            onChange={(e) => { setTo(e.target.value); setPage(0); }}
            className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
          />
        </div>
        <div>
          <label htmlFor="employee" className="block text-xs font-medium text-slate-600 mb-1">
            Nhân viên
          </label>
          <select
            id="employee"
            value={employeeId}
            onChange={(e) => { setEmployeeId(e.target.value); setPage(0); }}
            className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
          >
            <option value="">Tất cả nhân viên</option>
            {employees?.map((emp) => (
              <option key={emp.id} value={emp.id}>{emp.fullName}</option>
            ))}
          </select>
        </div>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <SkeletonCard key={i} />)}
        </div>
      ) : isError ? (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          Không thể tải dữ liệu chấm công. Vui lòng thử lại.
        </div>
      ) : records.length === 0 ? (
        <div className="text-center py-12 text-slate-500">
          <p className="text-lg">Không có bản ghi nào</p>
          <p className="text-sm mt-1">Thử chọn khoảng thời gian khác</p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-sm">
              <thead className="bg-slate-50">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Ngày</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Nhân viên</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Ca</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Vào (UTC+7)</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Ra (UTC+7)</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Trạng thái</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Override</th>
                  <th className="text-center px-4 py-3 font-medium text-slate-600">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {records.map((record) => (
                  <tr key={record.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 text-slate-700">{record.date}</td>
                    <td className="px-4 py-3 text-slate-700">{record.employeeName}</td>
                    <td className="px-4 py-3 text-slate-500">{record.shiftName ?? '—'}</td>
                    <td className="px-4 py-3 font-mono text-slate-600">{formatVN(record.checkInTime)}</td>
                    <td className="px-4 py-3 font-mono text-slate-600">{formatVN(record.checkOutTime)}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${ATTENDANCE_STATUS_COLORS[record.attendanceStatus as AttendanceStatus] ?? 'bg-slate-100 text-slate-600'}`}>
                        {ATTENDANCE_STATUS_LABEL[record.attendanceStatus as AttendanceStatus] ?? record.attendanceStatus}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      {record.isAdminOverride && (
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-purple-100 text-purple-800">
                          ADMIN_OVERRIDE
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <button
                        onClick={() => setOverridingRecord(record)}
                        aria-label={`Override bản ghi của ${record.employeeName}`}
                        className="px-3 py-1.5 text-xs bg-amber-500 text-white rounded-lg hover:bg-amber-600 min-h-[36px]"
                      >
                        Override
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 mt-4">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-2 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed min-h-[36px]"
              >
                Trước
              </button>
              <span className="text-sm text-slate-600">Trang {page + 1}/{totalPages}</span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-3 py-2 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed min-h-[36px]"
              >
                Sau
              </button>
            </div>
          )}
        </>
      )}

      {overridingRecord && (
        <AttendanceOverrideModal
          record={overridingRecord}
          onClose={() => setOverridingRecord(null)}
          onSuccess={handleSuccess}
        />
      )}
    </main>
  );
};
