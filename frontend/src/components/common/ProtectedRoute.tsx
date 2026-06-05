import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { UserRole } from '../../types/domain';

const ROLE_DEFAULT_ROUTES: Record<UserRole, string> = {
  [UserRole.EMPLOYEE]: '/check-in',
  [UserRole.LEADER]: '/leader/dashboard',
  [UserRole.ADMIN]: '/admin/shifts',
};

interface ProtectedRouteProps {
  allowedRoles: UserRole[];
  redirectTo?: string;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  allowedRoles,
  redirectTo = '/login',
}) => {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const user = useAuthStore((s) => s.user);

  if (!isAuthenticated || !user) {
    return <Navigate to={redirectTo} replace />;
  }

  if (!allowedRoles.includes(user.role)) {
    return <Navigate to={ROLE_DEFAULT_ROUTES[user.role]} replace />;
  }

  return <Outlet />;
};
