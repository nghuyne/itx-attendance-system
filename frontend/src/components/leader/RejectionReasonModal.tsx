import { useState, useEffect, useRef } from 'react';

interface RejectionReasonModalProps {
  isOpen: boolean;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
  isSubmitting: boolean;
}

export const RejectionReasonModal = ({ isOpen, onConfirm, onCancel, isSubmitting }: RejectionReasonModalProps) => {
  const [reason, setReason] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (isOpen) {
      setReason('');
      setTimeout(() => textareaRef.current?.focus(), 50);
    }
  }, [isOpen]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) onCancel();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onCancel]);

  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="rejection-reason-title"
      className="mt-4 p-4 border border-red-200 rounded-lg bg-red-50"
    >
      <p id="rejection-reason-title" className="text-sm font-medium text-red-800 mb-2">Lý do từ chối</p>
      <textarea
        ref={textareaRef}
        value={reason}
        onChange={e => setReason(e.target.value)}
        rows={4}
        placeholder="Nhập lý do từ chối..."
        aria-label="Lý do từ chối"
        className="w-full border border-red-300 rounded-lg p-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-red-400 bg-white"
      />
      <div className="flex gap-2 mt-3">
        <button
          onClick={() => onConfirm(reason)}
          disabled={reason.trim().length < 1 || isSubmitting}
          className="flex-1 min-h-[48px] bg-danger text-white rounded-lg text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed hover:bg-red-700 transition-colors"
        >
          {isSubmitting ? 'Đang xử lý...' : 'Xác nhận từ chối'}
        </button>
        <button
          onClick={onCancel}
          disabled={isSubmitting}
          className="flex-1 min-h-[48px] border border-slate-300 text-slate-700 rounded-lg text-sm hover:bg-slate-50 transition-colors"
        >
          Hủy
        </button>
      </div>
    </div>
  );
};
