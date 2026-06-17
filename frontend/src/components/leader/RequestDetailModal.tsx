import { useState, useEffect, useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { RequestSummaryDto } from '../../types/api';
import { requestService } from '../../services/requestService';
import { useUiStore } from '../../store/uiStore';
import { RejectionReasonModal } from './RejectionReasonModal';

interface RequestDetailModalProps {
  isOpen: boolean;
  request: RequestSummaryDto;
  onClose: () => void;
  onApproved?: () => void;
}

function formatDatetime(isoString: string | null): string {
  if (!isoString) return '—';
  const s = isoString.endsWith('Z') || isoString.includes('+') ? isoString : isoString + 'Z';
  return new Date(s).toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

const REQUEST_CATEGORY_LABEL: Record<string, string> = {
  EXCEPTION: 'Ngoại lệ',
  ADJUSTMENT: 'Điều chỉnh',
};

const REQUEST_TYPE_LABEL: Record<string, string> = {
  LATE_IN: 'Đi muộn',
  EARLY_OUT: 'Về sớm',
  HALF_DAY: 'Nửa ngày',
  LATE_IN_EARLY_OUT: 'Muộn & Về sớm',
};

export const RequestDetailModal = ({ isOpen, request, onClose, onApproved }: RequestDetailModalProps) => {
  const [showRejectForm, setShowRejectForm] = useState(false);
  const queryClient = useQueryClient();
  const showToast = useUiStore(s => s.showToast);
  const rejectButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement;
    return () => {
      previouslyFocused?.focus();
    };
  }, []);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const handleCancelReject = () => {
    setShowRejectForm(false);
    setTimeout(() => rejectButtonRef.current?.focus(), 0);
  };

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['leader', 'pending-requests'] });
    queryClient.invalidateQueries({ queryKey: ['leader', 'requests'] });
    queryClient.invalidateQueries({ queryKey: ['leader', 'team-roster'] });
  };

  const approveMutation = useMutation({
    mutationFn: () => requestService.approve(request.id),
    onSuccess: () => {
      invalidateAll();
      showToast({ type: 'success', message: 'Đã duyệt yêu cầu thành công' });
      onApproved?.();
      onClose();
    },
    onError: () => {
      showToast({ type: 'error', message: 'Có lỗi xảy ra, vui lòng thử lại' });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => requestService.reject(request.id, reason),
    onSuccess: () => {
      invalidateAll();
      showToast({ type: 'success', message: 'Đã từ chối yêu cầu' });
      onClose();
    },
    onError: () => {
      showToast({ type: 'error', message: 'Có lỗi xảy ra, vui lòng thử lại' });
    },
  });

  if (!isOpen) return null;

  const requestTypeLabel = request.requestCategory === 'ADJUSTMENT'
    ? 'Điều chỉnh giờ ra'
    : (REQUEST_TYPE_LABEL[request.requestType ?? ''] ?? request.requestType);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50"
      onClick={e => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        className="bg-white rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto"
      >
        <div className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 id="modal-title" className="text-lg font-semibold text-neutral">
              Chi tiết yêu cầu
            </h2>
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-slate-600 p-1 rounded"
              aria-label="Đóng"
            >
              ✕
            </button>
          </div>

          <dl className="space-y-3 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Nhân viên</dt>
              <dd className="font-medium">{request.employeeName}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Loại yêu cầu</dt>
              <dd className="font-medium">{REQUEST_CATEGORY_LABEL[request.requestCategory]} — {requestTypeLabel}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Ngày chấm công</dt>
              <dd className="font-medium">{request.attendanceDate}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Giờ vào</dt>
              <dd className="font-medium">{formatDatetime(request.checkInTime)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Giờ ra thực tế</dt>
              <dd className="font-medium">{formatDatetime(request.checkOutTime)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Lý do</dt>
              <dd className="font-medium text-right max-w-[60%]">{request.reason}</dd>
            </div>
            {request.requestCategory === 'ADJUSTMENT' && request.proposedCheckoutTime && (
              <div className="flex justify-between">
                <dt className="text-slate-500">Giờ ra đề xuất</dt>
                <dd className="font-medium">{formatDatetime(request.proposedCheckoutTime)}</dd>
              </div>
            )}
            <div className="flex justify-between">
              <dt className="text-slate-500">Ngày gửi</dt>
              <dd className="font-medium">{formatDatetime(request.createdAt)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Trạng thái</dt>
              <dd className="font-medium">{request.status}</dd>
            </div>
            {request.reviewReason && (
              <div className="flex justify-between">
                <dt className="text-slate-500">Lý do từ chối</dt>
                <dd className="font-medium text-red-600 text-right max-w-[60%]">{request.reviewReason}</dd>
              </div>
            )}
          </dl>

          {request.status === 'PENDING' && (
            <div className="mt-6 space-y-2">
              <div className="flex flex-col sm:flex-row gap-3">
                <button
                  onClick={() => approveMutation.mutate()}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                  className="w-full min-h-[48px] bg-success text-white rounded-lg font-medium disabled:opacity-50 hover:opacity-90 transition-opacity"
                >
                  {approveMutation.isPending ? 'Đang xử lý...' : 'Duyệt'}
                </button>
                <button
                  ref={rejectButtonRef}
                  onClick={() => setShowRejectForm(v => !v)}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                  className="w-full min-h-[48px] border border-danger text-danger rounded-lg font-medium disabled:opacity-50 hover:bg-red-50 transition-colors"
                >
                  Từ chối
                </button>
              </div>
              <RejectionReasonModal
                isOpen={showRejectForm}
                onConfirm={reason => rejectMutation.mutate(reason)}
                onCancel={handleCancelReject}
                isSubmitting={rejectMutation.isPending}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
