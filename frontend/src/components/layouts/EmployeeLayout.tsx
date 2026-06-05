import React from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { BottomTabNav } from '../common/BottomTabNav';

type EmployeeTab = 'check-in' | 'history' | 'requests' | 'settings';

const PATH_TO_TAB: Record<string, EmployeeTab> = {
  '/check-in': 'check-in',
  '/history': 'history',
  '/requests': 'requests',
  '/settings': 'settings',
};

const TAB_TO_PATH: Record<EmployeeTab, string> = {
  'check-in': '/check-in',
  'history': '/history',
  'requests': '/requests',
  'settings': '/settings',
};

export const EmployeeLayout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  const currentTab: EmployeeTab = PATH_TO_TAB[location.pathname] ?? 'check-in';

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <main className="flex-1 overflow-y-auto pb-16">
        <Outlet />
      </main>
      <BottomTabNav
        currentTab={currentTab}
        onTabChange={(tab) => navigate(TAB_TO_PATH[tab])}
      />
    </div>
  );
};
