import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { RequestSummaryDto } from '../../types/api';
import { requestService } from '../../services/requestService';
import { RequestDetailModal } from './RequestDetailModal';

type Tab = 'PENDING' | 'APPROVED' | 'REJECTED';

const TAB_LABEL: Record<Tab, string> = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
};

const REQUEST_CATEGORY_LABEL: Record<string, string> = {
  EXCEPTION: 'Ngoại lệ',
  ADJUSTMENT: 'Điều chỉnh',
};

function formatDatetime(isoString: string): string {
  return new Date(isoString).toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export const PendingRequestsScreen = () => {
  const [activeTab, setActiveTab] = useState<Tab>('PENDING');
  const [selectedRequest, setSelectedRequest] = useState<RequestSummaryDto | null>(null);

  const { data, isPending } = useQuery({
    queryKey: ['leader', 'pending-requests'],
    queryFn: requestService.getPending,
    refetchInterval: 15000,
    refetchIntervalInBackground: true,
  });

  const allRequests = data ?? [];
  const pendingCount = allRequests.filter(r => r.status === 'PENDING').length;
  const filtered = allRequests.filter(r => r.status === activeTab);

  return (
    <div>
      <div className="flex border-b border-slate-200 mb-4">
        {(['PENDING', 'APPROVED', 'REJECTED'] as Tab[]).map(tab => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
              activeTab === tab
                ? 'border-primary text-primary'
                : 'border-transparent text-slate-500 hover:text-neutral'
            }`}
          >
            {TAB_LABEL[tab]}
            {tab === 'PENDING' && pendingCount > 0 && (
              <span className="ml-2 px-1.5 py-0.5 bg-orange-100 text-orange-700 text-xs rounded-full">
                {pendingCount}
              </span>
            )}
          </button>
        ))}
      </div>

      {isPending ? (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="border rounded-lg p-4 animate-pulse">
              <div className="h-4 bg-slate-200 rounded w-1/3 mb-2" />
              <div className="h-3 bg-slate-200 rounded w-2/3 mb-2" />
              <div className="h-3 bg-slate-200 rounded w-1/2" />
            </div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <p className="text-slate-500 text-center py-8">
          {activeTab === 'PENDING' ? 'Không có yêu cầu chờ duyệt' : 'Danh sách trống'}
        </p>
      ) : (
        <div className="space-y-3">
          {filtered.map(req => (
            <button
              key={req.id}
              onClick={() => setSelectedRequest(req)}
              className="w-full text-left border border-slate-200 rounded-lg p-4 hover:border-primary hover:bg-primary/5 transition-colors"
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="font-semibold text-neutral">{req.employeeName}</p>
                  <p className="text-sm text-slate-500 mt-0.5">
                    {REQUEST_CATEGORY_LABEL[req.requestCategory]} · {req.attendanceDate}
                  </p>
                  <p className="text-sm text-slate-600 mt-1 line-clamp-2">{req.reason}</p>
                </div>
                <p className="text-xs text-slate-400 whitespace-nowrap flex-shrink-0">
                  {formatDatetime(req.createdAt)}
                </p>
              </div>
            </button>
          ))}
        </div>
      )}

      {selectedRequest && (
        <RequestDetailModal
          isOpen={true}
          request={selectedRequest}
          onClose={() => setSelectedRequest(null)}
        />
      )}
    </div>
  );
};
