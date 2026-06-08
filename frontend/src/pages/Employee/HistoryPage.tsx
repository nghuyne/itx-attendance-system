import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { attendanceService } from '../../services/attendanceService';
import { ATTENDANCE_STATUS_COLORS } from '../../types/domain';
import { SkeletonCard } from '../../components/common/SkeletonCard';
import type { AttendanceRecordDto } from '../../types/api';

const formatDate = (dateStr: string) =>
  new Date(dateStr + 'T00:00:00').toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });

const formatTimeVN = (utcStr: string | null) =>
  utcStr ? new Date(utcStr).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh' }) : '—';

const toIsoDate = (d: Date) => d.toISOString().slice(0, 10);

const STATUS_LABEL: Record<string, string> = {
  ON_TIME: 'Đúng giờ',
  LATE_IN: 'Đi muộn',
  EARLY_OUT: 'Về sớm',
  LATE_IN_EARLY_OUT: 'Muộn & Sớm',
  HALF_DAY: 'Nửa ngày',
  INCOMPLETE: 'Thiếu',
  ABSENT: 'Vắng',
};

function HistoryCard({ record }: { record: AttendanceRecordDto }) {
  return (
    <div className="bg-white rounded-lg border border-slate-200 p-4">
      <div className="flex items-center justify-between mb-2">
        <p className="text-sm font-medium text-slate-700">{formatDate(record.date)}</p>
        <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${ATTENDANCE_STATUS_COLORS[record.attendanceStatus]}`}>
          {STATUS_LABEL[record.attendanceStatus] ?? record.attendanceStatus}
        </span>
      </div>
      <div className="flex gap-6 text-sm text-slate-600">
        <div>
          <p className="text-xs text-slate-400">Check-in</p>
          <p className="font-mono font-medium">{formatTimeVN(record.checkInTime)}</p>
        </div>
        <div>
          <p className="text-xs text-slate-400">Check-out</p>
          <p className="font-mono font-medium">{formatTimeVN(record.checkOutTime)}</p>
        </div>
        {record.isClientSite && (
          <div className="ml-auto">
            <span className="text-xs bg-blue-50 text-blue-700 border border-blue-200 rounded px-2 py-0.5">Ngoài VP</span>
          </div>
        )}
      </div>
    </div>
  );
}

export const HistoryPage: React.FC = () => {
  const today = new Date();
  const thirtyDaysAgo = new Date(today);
  thirtyDaysAgo.setDate(today.getDate() - 30);

  const [from, setFrom] = useState(toIsoDate(thirtyDaysAgo));
  const [to, setTo] = useState(toIsoDate(today));
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['attendance', 'history', from, to, page],
    queryFn: () => attendanceService.getHistory({ from, to, page, size: 20 }),
  });

  const records = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  const handleDateChange = (field: 'from' | 'to', value: string) => {
    if (field === 'from') setFrom(value);
    else setTo(value);
    setPage(0);
  };

  return (
    <main className="p-4">
      <h1 className="text-2xl font-bold text-slate-700 mb-4">Lịch sử chấm công</h1>

      <div className="flex gap-2 mb-4">
        <div className="flex-1">
          <label className="text-xs text-slate-500 block mb-1" htmlFor="history-from">Từ ngày</label>
          <input
            id="history-from"
            type="date"
            value={from}
            max={to}
            onChange={e => handleDateChange('from', e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>
        <div className="flex-1">
          <label className="text-xs text-slate-500 block mb-1" htmlFor="history-to">Đến ngày</label>
          <input
            id="history-to"
            type="date"
            value={to}
            min={from}
            max={toIsoDate(today)}
            onChange={e => handleDateChange('to', e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>
      </div>

      {isLoading && (
        <div className="space-y-3">
          <SkeletonCard /><SkeletonCard /><SkeletonCard />
        </div>
      )}

      {isError && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          Không thể tải lịch sử. Vui lòng thử lại.
        </div>
      )}

      {!isLoading && !isError && records.length === 0 && (
        <div className="text-center py-12 text-slate-400">
          <p className="text-base">Không có bản ghi nào trong khoảng thời gian này.</p>
        </div>
      )}

      {!isLoading && !isError && records.length > 0 && (
        <>
          <div className="space-y-3 mb-4">
            {records.map(record => (
              <HistoryCard key={record.id} record={record} />
            ))}
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-between">
              <button
                onClick={() => setPage(p => p - 1)}
                disabled={page === 0}
                className="px-4 py-2 min-h-[48px] text-sm rounded-lg border border-slate-300 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                ← Trước
              </button>
              <p className="text-sm text-slate-500">Trang {page + 1} / {totalPages}</p>
              <button
                onClick={() => setPage(p => p + 1)}
                disabled={page >= totalPages - 1}
                className="px-4 py-2 min-h-[48px] text-sm rounded-lg border border-slate-300 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Sau →
              </button>
            </div>
          )}
        </>
      )}
    </main>
  );
};
