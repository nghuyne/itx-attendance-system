import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { requestService } from '../../services/requestService';
import { SkeletonCard } from '../../components/common/SkeletonCard';
import type { RequestSummaryDto, RequestStatus } from '../../types/api';

const REQUEST_TYPE_LABEL: Record<string, string> = {
  LATE_IN: 'Đi trễ',
  EARLY_OUT: 'Về sớm',
  HALF_DAY: 'Nửa ngày',
  LATE_IN_EARLY_OUT: 'Đi trễ & về sớm',
};

const STATUS_CONFIG: Record<RequestStatus, { label: string; className: string }> = {
  PENDING: { label: 'Chờ duyệt', className: 'bg-amber-100 text-amber-700' },
  APPROVED: { label: 'Đã duyệt', className: 'bg-green-100 text-green-700' },
  REJECTED: { label: 'Từ chối', className: 'bg-red-100 text-red-700' },
};

const FILTER_TABS: { key: 'ALL' | RequestStatus; label: string }[] = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ duyệt' },
  { key: 'APPROVED', label: 'Đã duyệt' },
  { key: 'REJECTED', label: 'Từ chối' },
];

const formatDate = (dateStr: string) =>
  new Date(dateStr + 'T00:00:00').toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });

const formatTimeVN = (utcStr: string | null) =>
  utcStr
    ? new Date(utcStr).toLocaleTimeString('vi-VN', {
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Asia/Ho_Chi_Minh',
      })
    : null;

function RequestCard({ req }: { req: RequestSummaryDto }) {
  const status = STATUS_CONFIG[req.status];
  const isAdjustment = req.requestCategory === 'ADJUSTMENT';

  return (
    <div className="bg-base-100 rounded-xl border border-base-200 shadow-sm p-4 space-y-2">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-neutral">{formatDate(req.attendanceDate)}</p>
          <p className="text-xs text-slate-500 mt-0.5">
            {isAdjustment
              ? 'Điều chỉnh giờ ra'
              : `Ngoại lệ — ${req.requestType ? REQUEST_TYPE_LABEL[req.requestType] ?? req.requestType : '—'}`}
          </p>
        </div>
        <span className={`shrink-0 text-xs font-medium px-2.5 py-1 rounded-full ${status.className}`}>
          {status.label}
        </span>
      </div>

      {isAdjustment && req.proposedCheckoutTime && (
        <p className="text-xs text-slate-600">
          Đề xuất giờ ra: <span className="font-medium">{formatTimeVN(req.proposedCheckoutTime)}</span>
        </p>
      )}

      {req.reason && (
        <p className="text-xs text-slate-600 line-clamp-2">
          Lý do: <span className="text-slate-700">{req.reason}</span>
        </p>
      )}

      {req.status === 'REJECTED' && req.reviewReason && (
        <div className="bg-red-50 rounded-lg px-3 py-2">
          <p className="text-xs text-red-600">
            Lý do từ chối: <span className="font-medium">{req.reviewReason}</span>
          </p>
        </div>
      )}
    </div>
  );
}

export const EmployeeRequestsPage: React.FC = () => {
  const [activeFilter, setActiveFilter] = useState<'ALL' | RequestStatus>('ALL');

  const { data, isLoading, isError } = useQuery({
    queryKey: ['myRequests'],
    queryFn: requestService.getMyRequests,
    staleTime: 30_000,
  });

  const filtered =
    activeFilter === 'ALL' ? (data ?? []) : (data ?? []).filter(r => r.status === activeFilter);

  return (
    <main className="p-4 space-y-4">
      <h1 className="text-2xl font-bold text-neutral">Yêu cầu của tôi</h1>

      {/* Filter tabs */}
      <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-hide">
        {FILTER_TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveFilter(tab.key)}
            className={`shrink-0 text-sm font-medium px-4 py-1.5 rounded-full transition-colors ${
              activeFilter === tab.key
                ? 'bg-primary text-primary-content'
                : 'bg-base-200 text-slate-600 hover:bg-base-300'
            }`}
          >
            {tab.label}
            {tab.key !== 'ALL' && data && (
              <span className="ml-1.5 text-xs opacity-70">
                ({data.filter(r => r.status === tab.key).length})
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Content */}
      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <SkeletonCard key={i} lines={3} />
          ))}
        </div>
      )}

      {isError && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-center">
          <p className="text-sm text-red-600 font-medium">Không thể tải danh sách yêu cầu</p>
          <p className="text-xs text-red-400 mt-1">Vui lòng thử lại sau</p>
        </div>
      )}

      {!isLoading && !isError && filtered.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <div className="w-16 h-16 rounded-full bg-base-200 flex items-center justify-center mb-4">
            <svg className="w-8 h-8 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
          </div>
          <p className="text-sm font-medium text-slate-600">
            {activeFilter === 'ALL' ? 'Chưa có yêu cầu nào' : 'Không có yêu cầu nào trong mục này'}
          </p>
          <p className="text-xs text-slate-400 mt-1">
            Gửi yêu cầu từ trang Lịch sử chấm công
          </p>
        </div>
      )}

      {!isLoading && !isError && filtered.length > 0 && (
        <div className="space-y-3">
          {filtered.map(req => (
            <RequestCard key={req.id} req={req} />
          ))}
        </div>
      )}
    </main>
  );
};
