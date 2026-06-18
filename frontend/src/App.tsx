import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastContainer } from './components/common/ToastContainer';
import { OfflineBanner } from './components/common/OfflineBanner';
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { EmployeeLayout } from './components/layouts/EmployeeLayout';
import { LeaderLayout } from './components/layouts/LeaderLayout';
import { AdminLayout } from './components/layouts/AdminLayout';
import { LoginPage } from './pages/LoginPage';
import { CheckInPage } from './pages/Employee/CheckInPage';
import { HistoryPage } from './pages/Employee/HistoryPage';
import { EmployeeRequestsPage } from './pages/Employee/RequestsPage';
import { SettingsPage } from './pages/Employee/SettingsPage';
import { LeaderDashboardPage } from './pages/Leader/DashboardPage';
import { LeaderRequestsPage } from './pages/Leader/RequestsPage';
import { ShiftsPage } from './pages/Admin/ShiftsPage';
import { IpsPage } from './pages/Admin/IpsPage';
import { HolidaysPage } from './pages/Admin/HolidaysPage';
import { AdminAttendancePage } from './pages/Admin/AttendancePage';
import { AdminRequestsPage } from './pages/Admin/RequestsPage';
import { AuditPage } from './pages/Admin/AuditPage';
import { OfficeLocationsPage } from './pages/Admin/OfficeLocationsPage';
import { ForceChangePasswordPage } from './pages/ForceChangePasswordPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { useAuthStore } from './store/authStore';
import { UserRole } from './types/domain';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 1000 * 60 * 5,
    },
  },
});

const ROLE_DEFAULT_ROUTES: Record<UserRole, string> = {
  [UserRole.EMPLOYEE]: '/check-in',
  [UserRole.LEADER]: '/leader/dashboard',
  [UserRole.ADMIN]: '/admin/shifts',
};

function App() {
  const user = useAuthStore((s) => s.user);
  const roleDefault = user ? ROLE_DEFAULT_ROUTES[user.role] : '/login';

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <OfflineBanner />
        <ToastContainer />
        <Routes>
          {/* Public */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />

          {/* Force change password — auth-only, no role or mustChangePassword restriction */}
          <Route element={<ProtectedRoute allowedRoles={[UserRole.EMPLOYEE, UserRole.LEADER, UserRole.ADMIN]} skipForceChangeCheck />}>
            <Route path="/change-password" element={<ForceChangePasswordPage />} />
          </Route>

          {/* Employee routes */}
          <Route element={<ProtectedRoute allowedRoles={[UserRole.EMPLOYEE]} />}>
            <Route element={<EmployeeLayout />}>
              <Route path="/check-in" element={<CheckInPage />} />
              <Route path="/history" element={<HistoryPage />} />
              <Route path="/requests" element={<EmployeeRequestsPage />} />
              <Route path="/settings" element={<SettingsPage />} />
            </Route>
          </Route>

          {/* Leader routes */}
          <Route element={<ProtectedRoute allowedRoles={[UserRole.LEADER]} />}>
            <Route element={<LeaderLayout />}>
              <Route path="/leader/dashboard" element={<LeaderDashboardPage />} />
              <Route path="/leader/requests" element={<LeaderRequestsPage />} />
            </Route>
          </Route>

          {/* Admin routes */}
          <Route element={<ProtectedRoute allowedRoles={[UserRole.ADMIN]} />}>
            <Route element={<AdminLayout />}>
              <Route path="/admin/shifts" element={<ShiftsPage />} />
              <Route path="/admin/ips" element={<IpsPage />} />
              <Route path="/admin/holidays" element={<HolidaysPage />} />
              <Route path="/admin/attendance" element={<AdminAttendancePage />} />
              <Route path="/admin/requests" element={<AdminRequestsPage />} />
              <Route path="/admin/audit" element={<AuditPage />} />
              <Route path="/admin/office-locations" element={<OfficeLocationsPage />} />
            </Route>
          </Route>

          {/* Default redirect */}
          <Route path="/" element={<Navigate to={roleDefault} replace />} />
          <Route path="*" element={<Navigate to={roleDefault} replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
