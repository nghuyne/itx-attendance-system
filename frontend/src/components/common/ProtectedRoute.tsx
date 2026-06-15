import React, { useState, useEffect } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { UserRole } from '../../types/domain';
import { LoadingSpinner } from './LoadingSpinner';

const ROLE_DEFAULT_ROUTES: Record<UserRole, string> = {
  [UserRole.EMPLOYEE]: '/check-in',
  [UserRole.LEADER]: '/leader/dashboard',
  [UserRole.ADMIN]: '/admin/shifts',
};

interface ProtectedRouteProps {
  allowedRoles: UserRole[];
  redirectTo?: string;
  skipForceChangeCheck?: boolean;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  allowedRoles,
  redirectTo = '/login',
  skipForceChangeCheck = false,
}) => {
  const [hasHydrated, setHasHydrated] = useState(useAuthStore.persist.hasHydrated());
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (!hasHydrated) {
      return useAuthStore.persist.onFinishHydration(() => setHasHydrated(true));
    }
  }, [hasHydrated]);

  if (!hasHydrated) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return <Navigate to={redirectTo} replace />;
  }

  if (!allowedRoles.includes(user.role)) {
    return <Navigate to={ROLE_DEFAULT_ROUTES[user.role]} replace />;
  }

  if (!skipForceChangeCheck && user.mustChangePassword) {
    return <Navigate to="/change-password" replace />;
  }

  return <Outlet />;
};
