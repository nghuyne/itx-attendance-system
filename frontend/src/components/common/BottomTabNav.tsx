import React from 'react';

type EmployeeTab = 'check-in' | 'history' | 'requests' | 'settings';

interface BottomTabNavProps {
  currentTab: EmployeeTab;
  onTabChange: (tab: EmployeeTab) => void;
  unreadRequestCount?: number;
}

const TABS: { id: EmployeeTab; label: string; icon: string }[] = [
  { id: 'check-in', label: 'Chấm công', icon: '📍' },
  { id: 'history', label: 'Lịch sử', icon: '📋' },
  { id: 'requests', label: 'Yêu cầu', icon: '📝' },
  { id: 'settings', label: 'Cài đặt', icon: '⚙️' },
];

export const BottomTabNav: React.FC<BottomTabNavProps> = ({
  currentTab,
  onTabChange,
  unreadRequestCount = 0,
}) => (
  <nav
    aria-label="Navigation"
    className="fixed bottom-0 left-0 right-0 h-16 bg-white border-t border-slate-200 flex z-30"
    role="tablist"
  >
    {TABS.map((tab) => {
      const isActive = tab.id === currentTab;
      const showBadge = tab.id === 'requests' && unreadRequestCount > 0;
      return (
        <button
          key={tab.id}
          role="tab"
          aria-selected={isActive}
          aria-label={tab.label}
          onClick={() => onTabChange(tab.id)}
          className={`flex-1 flex flex-col items-center justify-center min-h-[48px] gap-0.5 transition-colors ${
            isActive ? 'text-primary' : 'text-slate-400'
          }`}
        >
          <span className="relative text-xl leading-none">
            {tab.icon}
            {showBadge && (
              <span className="absolute -top-1 -right-1 w-4 h-4 bg-danger text-white text-xs rounded-full flex items-center justify-center">
                {unreadRequestCount > 9 ? '9+' : unreadRequestCount}
              </span>
            )}
          </span>
          <span className="text-xs font-medium">{tab.label}</span>
        </button>
      );
    })}
  </nav>
);
