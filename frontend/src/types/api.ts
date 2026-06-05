import type { UserRole, AttendanceStatus, ApprovalSubStatus, RequestType } from './domain';

export interface ApiResponse<T> {
  data: T;
  message?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

// Auth DTOs — Story 1.3
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  user: UserDto;
}

export interface UserDto {
  id: number;
  email: string;
  fullName: string;
  role: UserRole;
}

// Attendance DTOs — Story 3.x
export interface AttendanceRecordDto {
  id: number;
  userId: number;
  date: string;
  checkInTime: string | null;
  checkOutTime: string | null;
  status: AttendanceStatus;
  approvalSubStatus: ApprovalSubStatus | null;
  requestType: RequestType | null;
}
