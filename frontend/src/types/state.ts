import type { UserRole } from './domain';

export interface AuthUser {
  id: number;
  email: string;
  fullName: string;
  role: UserRole;
}

export interface NotificationState {
  message: string;
  type: 'success' | 'error' | 'warning';
}

export interface PaginationState {
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
}
