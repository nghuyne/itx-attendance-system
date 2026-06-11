import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { adminService } from '../../services/adminService';

const getDefaultDates = () => {
  const to = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
  const fromDate = new Date();
  fromDate.setDate(fromDate.getDate() - 7);
  const from = fromDate.toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
  return { from, to };
};

const formatVNDateTime = (isoStr: string): string => {
  const utcStr = isoStr.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(isoStr) ? isoStr : `${isoStr}Z`;
  return new Date(utcStr).toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

export const AuditPage: React.FC = () => {
  const defaults = getDefaultDates();
  const [from, setFrom] = useState(defaults.from);
  const [to, setTo] = useState(defaults.to);
  const [adminId, setAdminId] = useState('');
  const [targetTable, setTargetTable] = useState('');
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'audit-logs', from, to, adminId, targetTable, page],
    queryFn: () =>
      adminService.getAuditLogs(from, to, page, 50, adminId || undefined, targetTable || undefined),
  });

  const { data: admins, isError: isAdminsError } = useQuery({
    queryKey: ['admin', 'admins'],
    queryFn: () => adminService.getAdmins(),
    staleTime: 5 * 60 * 1000,
  });

  return (
    <main className="p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-slate-700">Audit Logs</h1>
      </div>

      <div className="flex flex-wrap gap-3 mb-4 p-4 bg-slate-50 rounded-lg border border-slate-200">
        <div>
          <label htmlFor="from" className="block text-xs font-medium text-slate-600 mb-1">Từ ngày</label>
          <input
            id="from"
            type="date"
            value={from}
            onChange={e => { setFrom(e.target.value); setPage(0); }}
            className="min-h-[48px] border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
          />
        </div>
        <div>
          <label htmlFor="to" className="block text-xs font-medium text-slate-600 mb-1">Đến ngày</label>
          <input
            id="to"
            type="date"
            value={to}
            onChange={e => { setTo(e.target.value); setPage(0); }}
            className="min-h-[48px] border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
          />
        </div>
        <div>
          <label htmlFor="adminFilter" className="block text-xs font-medium text-slate-600 mb-1">Admin</label>
          <select
            id="adminFilter"
            value={adminId}
            onChange={e => { setAdminId(e.target.value); setPage(0); }}
            className="min-h-[48px] border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
          >
            <option value="">Tất cả admin</option>
            {(admins ?? []).map(a => (
              <option key={a.id} value={a.id}>{a.fullName}</option>
            ))}
          </select>
          {isAdminsError && <p className="text-xs text-danger mt-1">Không tải được danh sách admin</p>}
        </div>
        <div>
          <label htmlFor="tableFilter" className="block text-xs font-medium text-slate-600 mb-1">Bảng</label>
          <select
            id="tableFilter"
            value={targetTable}
            onChange={e => { setTargetTable(e.target.value); setPage(0); }}
            className="min-h-[48px] border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600"
          >
            <option value="">Tất cả bảng</option>
            <option value="attendance_records">attendance_records</option>
          </select>
        </div>
      </div>

      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-10 bg-slate-200 animate-pulse rounded" />
          ))}
        </div>
      )}

      {isError && (
        <div className="text-center py-8 text-danger">
          Không thể tải audit logs. Vui lòng thử lại.
        </div>
      )}

      {!isLoading && !isError && data?.content.length === 0 && (
        <div className="text-center py-8 text-slate-500">
          Không có audit log nào trong khoảng thời gian này.
        </div>
      )}

      {!isLoading && !isError && data && data.content.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-sm">
              <thead className="bg-slate-50">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Thời gian (UTC+7)</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Admin</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Bảng</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Record ID</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Trường</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Giá trị cũ</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Giá trị mới</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Lý do</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map(log => (
                  <tr key={log.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 text-slate-700 whitespace-nowrap">
                      {formatVNDateTime(log.createdAt)}
                    </td>
                    <td className="px-4 py-3 text-slate-700">{log.adminName}</td>
                    <td className="px-4 py-3 text-slate-500 font-mono text-xs">{log.targetTable}</td>
                    <td className="px-4 py-3 font-mono text-xs text-slate-600" title={log.targetId ?? undefined}>
                      {log.targetId ? (log.targetId.length > 8 ? `${log.targetId.slice(0, 8)}…` : log.targetId) : '—'}
                    </td>
                    <td className="px-4 py-3 text-slate-600">{log.fieldChanged}</td>
                    <td className="px-4 py-3 text-slate-500">{log.oldValue ?? '—'}</td>
                    <td className="px-4 py-3 text-slate-700">{log.newValue ?? '—'}</td>
                    <td className="px-4 py-3 text-slate-600 max-w-xs truncate" title={log.reason ?? undefined}>
                      {log.reason ?? '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex items-center gap-2 justify-end mt-4">
            <button
              onClick={() => setPage(p => p - 1)}
              disabled={page === 0}
              className="min-h-[48px] px-3 py-1.5 rounded border text-sm disabled:opacity-50 hover:bg-slate-50"
            >
              Trước
            </button>
            <span className="text-sm text-slate-600">
              Trang {(data.number ?? 0) + 1}/{data.totalPages ?? 1}
            </span>
            <button
              onClick={() => setPage(p => p + 1)}
              disabled={page >= (data.totalPages ?? 1) - 1}
              className="min-h-[48px] px-3 py-1.5 rounded border text-sm disabled:opacity-50 hover:bg-slate-50"
            >
              Sau
            </button>
          </div>
        </>
      )}
    </main>
  );
};
