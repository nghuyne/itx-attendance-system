import React from 'react';
import { Outlet } from 'react-router-dom';
import { Header } from '../common/Header';
import { Sidebar } from '../common/Sidebar';
import { useUiStore } from '../../store/uiStore';
import { UserRole } from '../../types/domain';

export const LeaderLayout: React.FC = () => {
  const isSidebarOpen = useUiStore((s) => s.isSidebarOpen);
  const setSidebarOpen = useUiStore((s) => s.setSidebarOpen);

  return (
    <div className="min-h-screen bg-background flex">
      <Sidebar
        isOpen={isSidebarOpen}
        onClose={() => setSidebarOpen(false)}
        role={UserRole.LEADER}
      />
      <div className="flex-1 flex flex-col min-w-0">
        <Header
          title="Quản lý Nhóm"
          onMenuClick={() => setSidebarOpen(!isSidebarOpen)}
        />
        <main className="flex-1 p-4 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
