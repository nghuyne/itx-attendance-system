import React, { useState, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { attendanceService } from '../../services/attendanceService';
import { ATTENDANCE_STATUS_COLORS } from '../../types/domain';
import { SkeletonCard } from '../../components/common/SkeletonCard';
import { CameraViewfinder } from '../../components/employee/CameraViewfinder';
import { ClientSiteModeToggle } from '../../components/employee/ClientSiteModeToggle';
import { useUiStore } from '../../store/uiStore';
import type { CheckOutRequest } from '../../types/api';

export const CheckInPage: React.FC = () => {
  const queryClient = useQueryClient();
  const showToast = useUiStore(s => s.showToast);

  const { data: todayRecord, isLoading, isError } = useQuery({
    queryKey: ['attendance', 'today'],
    queryFn: attendanceService.getTodayRecord,
  });

  const [photoBase64, setPhotoBase64] = useState<string>('');
  const [coords, setCoords] = useState<{ lat: number | null; lng: number | null }>({
    lat: null,
    lng: null,
  });
  const [isClientSite, setIsClientSite] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [checkOutPhoto, setCheckOutPhoto] = useState<string>('');
  const [isCheckingOut, setIsCheckingOut] = useState(false);
  const [checkOutError, setCheckOutError] = useState<string | null>(null);

  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => setCoords({ lat: null, lng: null }),
      { timeout: 10_000, enableHighAccuracy: true }
    );
  }, []);

  const handlePhotoCapture = (base64: string) => {
    setPhotoBase64(base64);
    setSubmitError(null);
  };

  const handleCameraError = (error: string) => {
    showToast({ type: 'error', message: error });
  };

  const handleSubmit = async () => {
    if (!photoBase64) return;
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      await attendanceService.checkIn({
        lat: coords.lat,
        lng: coords.lng,
        photoBase64,
        isClientSite,
      });
      await queryClient.invalidateQueries({ queryKey: ['attendance', 'today'] });
      showToast({ type: 'success', message: 'Check-in thành công!' });
    } catch (err) {
      const errorCode = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      const message =
        errorCode === 'GPS_REQUIRED' ? 'GPS bắt buộc khi chấm công ngoài văn phòng.' :
        errorCode === 'INVALID_IP' ? 'Không nhận diện được mạng văn phòng. Kiểm tra kết nối.' :
        errorCode === 'ALREADY_CHECKED_IN' ? 'Bạn đã chấm công rồi hôm nay.' :
        errorCode === 'PHOTO_UPLOAD_FAILED' ? 'Lỗi tải ảnh. Vui lòng thử lại.' :
        errorCode === 'PHOTO_TOO_LARGE' ? 'Ảnh quá lớn (>500KB). Vui lòng chụp lại.' :
        'Lỗi không xác định. Vui lòng thử lại.';
      setSubmitError(message);
      showToast({ type: 'error', message });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCheckOut = async () => {
    if (!checkOutPhoto) return;
    setIsCheckingOut(true);
    setCheckOutError(null);
    try {
      await attendanceService.checkOut({
        lat: coords.lat,
        lng: coords.lng,
        photoBase64: checkOutPhoto,
      } as CheckOutRequest);
      await queryClient.invalidateQueries({ queryKey: ['attendance', 'today'] });
      showToast({ type: 'success', message: 'Check-out thành công!' });
    } catch (err) {
      const errorCode = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      const message =
        errorCode === 'ALREADY_CHECKED_OUT' ? 'Bạn đã check-out rồi hôm nay.' :
        errorCode === 'NO_CHECKIN_FOUND' ? 'Không tìm thấy bản ghi check-in hôm nay.' :
        errorCode === 'PHOTO_UPLOAD_FAILED' ? 'Lỗi tải ảnh. Vui lòng thử lại.' :
        'Lỗi không xác định. Vui lòng thử lại.';
      setCheckOutError(message);
      showToast({ type: 'error', message });
    } finally {
      setIsCheckingOut(false);
    }
  };

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
    const formatVN = (utcStr: string | null) =>
      utcStr ? new Date(utcStr).toLocaleTimeString('vi-VN', {
        hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh',
      }) : '—';

    if (todayRecord.checkOutTime) {
      return (
        <main className="p-4">
          <h1 className="text-2xl font-bold text-slate-700 mb-4">Chấm công</h1>
          <div className="bg-white rounded-lg border border-slate-200 p-4 space-y-3">
            <p className="text-sm text-slate-500">
              Ca: {todayRecord.shiftName} ({todayRecord.shiftStartTime}–{todayRecord.shiftEndTime})
            </p>
            <div className="flex gap-6">
              <div>
                <p className="text-xs text-slate-500">Check-in</p>
                <p className="text-xl font-bold text-slate-800 font-mono">{formatVN(todayRecord.checkInTime)}</p>
              </div>
              <div>
                <p className="text-xs text-slate-500">Check-out</p>
                <p className="text-xl font-bold text-slate-800 font-mono">{formatVN(todayRecord.checkOutTime)}</p>
              </div>
            </div>
            <span className={`inline-flex px-2 py-1 rounded-full text-xs font-medium ${ATTENDANCE_STATUS_COLORS[todayRecord.attendanceStatus]}`}>
              {todayRecord.attendanceStatus}
            </span>
          </div>
        </main>
      );
    }

    const canCheckOut = checkOutPhoto.length > 0 && !isCheckingOut;
    return (
      <main className="flex flex-col min-h-0">
        <div className="p-4 pb-0">
          <h1 className="text-2xl font-bold text-slate-700 mb-2">Chấm công</h1>
          <div className="bg-white rounded-lg border border-slate-200 p-3 mb-3">
            <p className="text-sm text-slate-500">Check-in lúc</p>
            <p className="text-xl font-bold text-slate-800 font-mono">{formatVN(todayRecord.checkInTime)}</p>
            <span className={`inline-flex mt-1 px-2 py-1 rounded-full text-xs font-medium ${ATTENDANCE_STATUS_COLORS[todayRecord.attendanceStatus]}`}>
              {todayRecord.attendanceStatus}
            </span>
          </div>
          <p className="text-sm font-medium text-slate-700 mb-2">Chụp ảnh để check-out:</p>
        </div>

        <div className="px-4">
          <CameraViewfinder
            onPhotoCapture={(base64) => { setCheckOutPhoto(base64); setCheckOutError(null); }}
            onError={(error) => showToast({ type: 'error', message: error })}
          />
        </div>

        <div className="p-4 space-y-2 mt-2">
          <button
            onClick={handleCheckOut}
            disabled={!canCheckOut}
            className={`w-full py-3 min-h-[48px] rounded-lg font-bold text-lg transition-all ${
              canCheckOut
                ? 'bg-emerald-600 text-white hover:bg-emerald-700 active:scale-95'
                : 'bg-slate-300 text-slate-500 cursor-not-allowed'
            } ${isCheckingOut ? 'opacity-75' : ''}`}
          >
            {isCheckingOut ? 'Đang xử lý...' : 'Xác nhận Check-out'}
          </button>
          {checkOutError && (
            <div className="bg-red-50 border border-red-300 rounded-lg p-3">
              <p className="text-red-700 text-sm">{checkOutError}</p>
            </div>
          )}
        </div>
      </main>
    );
  }

  const canSubmit = photoBase64.length > 0
    && !isSubmitting
    && (!isClientSite || coords.lat !== null);

  return (
    <main className="flex flex-col min-h-0">
      <div className="p-4 pb-0">
        <h1 className="text-2xl font-bold text-slate-700 mb-4">Chấm công</h1>
        {coords.lat !== null && coords.lng !== null ? (
          <p className="text-xs text-emerald-600 mb-2">GPS: {coords.lat.toFixed(4)}, {coords.lng.toFixed(4)}</p>
        ) : (
          <p className="text-xs text-amber-500 mb-2">GPS không khả dụng — check-in văn phòng vẫn được phép</p>
        )}
      </div>

      <div className="px-4 mb-3">
        <ClientSiteModeToggle
          isClientSite={isClientSite}
          onChange={setIsClientSite}
          gpsAvailable={coords.lat !== null}
        />
      </div>

      <div className="px-4">
        <CameraViewfinder
          onPhotoCapture={handlePhotoCapture}
          onError={handleCameraError}
        />
      </div>

      <div className="p-4 space-y-2 mt-2">
        <button
          onClick={handleSubmit}
          disabled={!canSubmit}
          className={`w-full py-3 min-h-[48px] rounded-lg font-bold text-lg transition-all ${
            canSubmit
              ? 'bg-emerald-600 text-white hover:bg-emerald-700 active:scale-95'
              : 'bg-slate-300 text-slate-500 cursor-not-allowed'
          } ${isSubmitting ? 'opacity-75' : ''}`}
        >
          {isSubmitting ? 'Đang xử lý...' : 'Xác nhận Check-in'}
        </button>

        {submitError && (
          <div className="bg-red-50 border border-red-300 rounded-lg p-3">
            <p className="text-red-700 text-sm">{submitError}</p>
          </div>
        )}
      </div>
    </main>
  );
};
