import React from 'react';

interface ClientSiteModeToggleProps {
  isClientSite: boolean;
  onChange: (value: boolean) => void;
  gpsAvailable: boolean;
}

export const ClientSiteModeToggle: React.FC<ClientSiteModeToggleProps> = ({
  isClientSite,
  onChange,
  gpsAvailable,
}) => {
  return (
    <div className="bg-white rounded-lg border border-slate-200 p-3">
      <div className="flex items-center justify-between min-h-[48px]">
        <div>
          <p className="text-sm font-medium text-slate-700">
            {isClientSite ? 'Ngoài văn phòng' : 'Office Mode'}
          </p>
          <p className="text-xs text-slate-500 mt-0.5">
            {isClientSite
              ? 'Xác thực IP bị bỏ qua — GPS bắt buộc'
              : 'Xác thực IP văn phòng đang hoạt động'}
          </p>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={isClientSite}
          aria-label="Chế độ ngoài văn phòng"
          onClick={() => onChange(!isClientSite)}
          className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 ${
            isClientSite ? 'bg-emerald-600' : 'bg-slate-300'
          }`}
        >
          <span
            className={`inline-block h-4 w-4 rounded-full bg-white shadow transition-transform ${
              isClientSite ? 'translate-x-6' : 'translate-x-1'
            }`}
          />
        </button>
      </div>

      {isClientSite && !gpsAvailable && (
        <div className="mt-2 bg-amber-50 border border-amber-200 rounded p-2">
          <p className="text-xs text-amber-700 font-medium">
            GPS chưa khả dụng — Đang đợi tín hiệu GPS để tiếp tục
          </p>
        </div>
      )}
    </div>
  );
};
