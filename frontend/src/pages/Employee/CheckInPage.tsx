import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { attendanceService } from '../../services/attendanceService';
import { ATTENDANCE_STATUS_COLORS } from '../../types/domain';
import { SkeletonCard } from '../../components/common/SkeletonCard';

export const CheckInPage: React.FC = () => {
  const { data: todayRecord, isLoading, isError } = useQuery({
    queryKey: ['attendance', 'today'],
    queryFn: attendanceService.getTodayRecord,
  });

  if (isLoading) {
    return (
      <main className="p-4">
        <h1 className="text-2xl font-bold text-slate-700 mb-4">Chấm công</h1>
        <SkeletonCard />
      </main>
    );
  }

  if (isError) {
    return (
      <main className="p-4">
        <h1 className="text-2xl font-bold text-slate-700 mb-4">Chấm công</h1>
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          Không thể tải thông tin chấm công. Vui lòng thử lại.
        </div>
      </main>
    );
  }

  if (todayRecord) {
    const checkInTimeVN = new Date(todayRecord.checkInTime!).toLocaleTimeString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'Asia/Ho_Chi_Minh',
    });
    return (
      <main className="p-4">
        <h1 className="text-2xl font-bold text-slate-700 mb-4">Chấm công</h1>
        <div className="bg-white rounded-lg border border-slate-200 p-4 space-y-3">
          <p className="text-sm text-slate-500">
            Ca: {todayRecord.shiftName} ({todayRecord.shiftStartTime}–{todayRecord.shiftEndTime})
          </p>
          <div>
            <p className="text-sm text-slate-500">Đã check-in lúc</p>
            <p className="text-2xl font-bold text-slate-800 font-mono">{checkInTimeVN}</p>
          </div>
          <span
            className={`inline-flex px-2 py-1 rounded-full text-xs font-medium ${ATTENDANCE_STATUS_COLORS[todayRecord.attendanceStatus]}`}
          >
            {todayRecord.attendanceStatus}
          </span>
          {!todayRecord.checkOutTime && (
            <p className="text-sm text-amber-600">Chưa check-out</p>
          )}
        </div>
      </main>
    );
  }

  return (
    <main className="p-4">
      <h1 className="text-2xl font-bold text-slate-700 mb-4">Chấm công</h1>
      <div className="bg-slate-50 border border-slate-200 rounded-lg p-6 text-center">
        <p className="text-slate-600 font-medium">Chưa có bản ghi hôm nay</p>
        <p className="text-sm text-slate-400 mt-1">Camera check-in sẽ có ở Story 3.2</p>
      </div>
    </main>
  );
};
