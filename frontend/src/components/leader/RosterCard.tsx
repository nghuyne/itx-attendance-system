import type { TeamRosterItemDto, RequestCategory } from '../../types/api';
import { AttendanceStatus, ATTENDANCE_STATUS_LABEL, ATTENDANCE_STATUS_COLORS } from '../../types/domain';

interface RosterCardProps {
  item: TeamRosterItemDto;
  onRequestDetail?: (requestId: string, category: RequestCategory) => void;
}

const BORDER_COLOR: Record<string, string> = {
  [AttendanceStatus.ON_TIME]: 'border-l-green-500',
  [AttendanceStatus.LATE_IN]: 'border-l-amber-500',
  [AttendanceStatus.EARLY_OUT]: 'border-l-amber-500',
  [AttendanceStatus.LATE_IN_EARLY_OUT]: 'border-l-amber-500',
  [AttendanceStatus.HALF_DAY]: 'border-l-amber-500',
  [AttendanceStatus.INCOMPLETE]: 'border-l-orange-500',
  [AttendanceStatus.ABSENT]: 'border-l-slate-400',
};

function formatTime(isoString: string | null): string {
  if (!isoString) return '--:--';
  return new Date(isoString).toLocaleTimeString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export const RosterCard = ({ item, onRequestDetail }: RosterCardProps) => {
  const borderColor = item.attendanceStatus
    ? (BORDER_COLOR[item.attendanceStatus] ?? 'border-l-slate-400')
    : 'border-l-slate-400';

  const statusBadge = item.attendanceStatus ? (
    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${ATTENDANCE_STATUS_COLORS[item.attendanceStatus]}`}>
      {ATTENDANCE_STATUS_LABEL[item.attendanceStatus]}
    </span>
  ) : (
    <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-600">
      Chưa check-in
    </span>
  );

  const shiftLabel = item.shiftName
    ? `${item.shiftName}: ${item.shiftStartTime ?? '--'} - ${item.shiftEndTime ?? '--'}`
    : 'Chưa xếp ca';

  return (
    <div className={`border border-slate-200 border-l-4 ${borderColor} rounded-lg p-4 bg-white`}>
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <p className="font-semibold text-neutral truncate">{item.employeeName}</p>
            {item.hasPendingRequest && (
              <span className="w-2 h-2 rounded-full bg-orange-400 flex-shrink-0" title="Có yêu cầu đang chờ" />
            )}
          </div>
          <p className="text-xs text-slate-500 mt-0.5">{shiftLabel}</p>
          <div className="flex items-center gap-3 mt-1 text-xs text-slate-600">
            <span>Vào: {formatTime(item.checkInTime)}</span>
            <span>Ra: {formatTime(item.checkOutTime)}</span>
          </div>
        </div>
        <div className="flex flex-col items-end gap-2">
          {statusBadge}
          {item.hasPendingRequest && item.pendingRequestId && item.pendingRequestCategory && (
            <button
              onClick={() => onRequestDetail?.(item.pendingRequestId!, item.pendingRequestCategory!)}
              className="text-xs text-primary font-medium min-h-[48px] px-3 border border-primary rounded-lg hover:bg-primary/5 transition-colors"
            >
              Xem chi tiết
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
