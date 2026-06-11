import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useUiStore } from '../../store/uiStore';
import type { RequestSummaryDto, RequestCategory } from '../../types/api';
import { AttendanceStatus } from '../../types/domain';
import { leaderService } from '../../services/leaderService';
import { requestService } from '../../services/requestService';
import { RosterCard } from './RosterCard';
import { RequestDetailModal } from './RequestDetailModal';

function todayVN(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
}

function addDays(dateStr: string, days: number): string {
  const d = new Date(dateStr + 'T00:00:00+07:00');
  d.setDate(d.getDate() + days);
  return d.toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
}

export const DailyRosterScreen = () => {
  const [selectedDate, setSelectedDate] = useState(todayVN);
  const [selectedRequest, setSelectedRequest] = useState<RequestSummaryDto | null>(null);
  const showToast = useUiStore((s) => s.showToast);

  const { data, isPending } = useQuery({
    queryKey: ['leader', 'team-roster', selectedDate],
    queryFn: () => leaderService.getTeamRoster(selectedDate),
  });

  const pendingQuery = useQuery({
    queryKey: ['leader', 'pending-requests'],
    queryFn: requestService.getPending,
  });

  const handleRequestDetail = async (requestId: string, _category: RequestCategory) => {
    const found = (pendingQuery.data ?? []).find(r => r.id === requestId);
    if (found) {
      setSelectedRequest(found);
      return;
    }
    try {
      const result = await pendingQuery.refetch();
      const refound = (result.data ?? []).find(r => r.id === requestId);
      if (refound) {
        setSelectedRequest(refound);
      } else {
        showToast({ type: 'warning', message: 'Yêu cầu không còn tồn tại' });
      }
    } catch {
      showToast({ type: 'error', message: 'Không thể tải thông tin yêu cầu' });
    }
  };

  const roster = data ?? [];
  const onTimeCount = roster.filter(r => r.attendanceStatus === AttendanceStatus.ON_TIME).length;
  const lateCount = roster.filter(r =>
    r.attendanceStatus === AttendanceStatus.LATE_IN ||
    r.attendanceStatus === AttendanceStatus.EARLY_OUT ||
    r.attendanceStatus === AttendanceStatus.LATE_IN_EARLY_OUT ||
    r.attendanceStatus === AttendanceStatus.HALF_DAY
  ).length;
  const incompleteCount = roster.filter(r => r.attendanceStatus === AttendanceStatus.INCOMPLETE).length;
  const absentCount = roster.filter(r => r.attendanceStatus === AttendanceStatus.ABSENT).length;

  return (
    <div>
      <div className="flex items-center gap-2 mb-4 flex-wrap">
        <button
          onClick={() => setSelectedDate(d => addDays(d, -1))}
          className="min-h-[48px] px-3 border border-slate-300 rounded-lg text-sm hover:bg-slate-50 transition-colors"
        >
          ← Ngày trước
        </button>
        <button
          onClick={() => setSelectedDate(todayVN())}
          className="min-h-[48px] px-3 border border-primary text-primary rounded-lg text-sm hover:bg-primary/5 transition-colors"
        >
          Hôm nay
        </button>
        <button
          onClick={() => setSelectedDate(d => addDays(d, 1))}
          className="min-h-[48px] px-3 border border-slate-300 rounded-lg text-sm hover:bg-slate-50 transition-colors"
        >
          Ngày sau →
        </button>
        <input
          type="date"
          value={selectedDate}
          onChange={e => setSelectedDate(e.target.value)}
          className="min-h-[48px] border border-slate-300 rounded-lg px-3 text-sm"
        />
      </div>

      <div className="grid grid-cols-4 gap-3 mb-4">
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 text-center">
          <p className="text-xl font-bold text-green-700">{onTimeCount}</p>
          <p className="text-xs text-green-600">Đúng giờ</p>
        </div>
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-center">
          <p className="text-xl font-bold text-amber-700">{lateCount}</p>
          <p className="text-xs text-amber-600">Đi muộn</p>
        </div>
        <div className="bg-orange-50 border border-orange-200 rounded-lg p-3 text-center">
          <p className="text-xl font-bold text-orange-700">{incompleteCount}</p>
          <p className="text-xs text-orange-600">Thiếu</p>
        </div>
        <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 text-center">
          <p className="text-xl font-bold text-slate-700">{absentCount}</p>
          <p className="text-xs text-slate-600">Vắng</p>
        </div>
      </div>

      {isPending ? (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="border rounded-lg p-4 animate-pulse">
              <div className="h-4 bg-slate-200 rounded w-1/3 mb-2" />
              <div className="h-3 bg-slate-200 rounded w-1/2 mb-2" />
              <div className="h-3 bg-slate-200 rounded w-1/4" />
            </div>
          ))}
        </div>
      ) : roster.length === 0 ? (
        <p className="text-slate-500 text-center py-8">Không có nhân viên trong nhóm</p>
      ) : (
        <div className="space-y-3">
          {roster.map(item => (
            <RosterCard
              key={item.employeeId}
              item={item}
              onRequestDetail={handleRequestDetail}
            />
          ))}
        </div>
      )}

      {selectedRequest && (
        <RequestDetailModal
          isOpen={true}
          request={selectedRequest}
          onClose={() => setSelectedRequest(null)}
          onApproved={() => setSelectedRequest(null)}
        />
      )}
    </div>
  );
};
