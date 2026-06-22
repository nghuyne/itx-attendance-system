import React, { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { authService } from '../../services/authService';
import { useAuthStore } from '../../store/authStore';
import { useUiStore } from '../../store/uiStore';
import { UserRole } from '../../types/domain';

interface SidebarLink {
  to: string;
  label: string;
  icon: string;
}

const LEADER_LINKS: SidebarLink[] = [
  { to: '/leader/dashboard', label: 'Dashboard', icon: '📊' },
  { to: '/leader/requests', label: 'Yêu cầu', icon: '📝' },
];

const ADMIN_LINKS: SidebarLink[] = [
  { to: '/admin/shifts', label: 'Ca làm việc', icon: '🕐' },
  { to: '/admin/ips', label: 'Quản lý IP', icon: '🌐' },
  { to: '/admin/office-locations', label: 'Vị trí VP', icon: '📍' },
  { to: '/admin/holidays', label: 'Ngày lễ', icon: '📅' },
  { to: '/admin/attendance', label: 'Chấm công', icon: '📋' },
  { to: '/admin/requests', label: 'Yêu cầu', icon: '📝' },
  { to: '/admin/audit', label: 'Audit Logs', icon: '🔍' },
  { to: '/admin/departments', label: 'Phòng ban', icon: '🏢' },
];

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
  role: UserRole;
}

export const Sidebar: React.FC<SidebarProps> = ({ isOpen, onClose, role }) => {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const setLoading = useUiStore((s) => s.setLoading);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const links = role === UserRole.ADMIN ? ADMIN_LINKS : LEADER_LINKS;

  const handleLogout = async () => {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    setLoading(true);
    try {
      await authService.logout();
    } finally {
      clearAuth();
      setLoading(false);
      setIsLoggingOut(false);
      navigate('/login', { replace: true });
    }
  };

  return (
    <>
      {/* Mobile overlay backdrop */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 md:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <nav
        aria-label="Sidebar navigation"
        aria-expanded={isOpen}
        className={`
          fixed md:static inset-y-0 left-0 z-50 md:z-auto
          bg-white border-r border-slate-200
          flex flex-col
          transition-all duration-200
          ${isOpen ? 'w-64' : 'w-0 md:w-64'}
          overflow-hidden
        `}
      >
        <div className="p-4 border-b border-slate-200 shrink-0">
          <p className="font-bold text-primary text-lg">ITX Attendance</p>
          <p className="text-sm text-slate-500 truncate">{user?.fullName}</p>
        </div>

        <ul className="flex-1 overflow-y-auto p-2">
          {links.map((link) => (
            <li key={link.to}>
              <NavLink
                to={link.to}
                onClick={() => onClose()}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-primary/10 text-primary'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`
                }
              >
                <span>{link.icon}</span>
                {link.label}
              </NavLink>
            </li>
          ))}
        </ul>

        <div className="p-2 border-t border-slate-200 shrink-0">
          <button
            onClick={handleLogout}
            disabled={isLoggingOut}
            aria-label="Đăng xuất"
            className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-slate-600 hover:bg-slate-100 w-full transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <span>🚪</span>
            Đăng xuất
          </button>
        </div>
      </nav>
    </>
  );
};
