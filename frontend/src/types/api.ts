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

// Auth DTOs — matches AuthResponse.java exactly
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  user: UserDto;
}

export interface UserDto {
  id: string;
  username: string;
  fullName: string;
  role: UserRole;
}

// Shift DTOs — Story 2.1
export interface ShiftDto {
  id: string;
  name: string;
  startTime: string;
  endTime: string;
  checkInOpenMinutes: number;
  lateInThreshold: number;
  earlyOutThreshold: number;
  halfDayThreshold: number;
  otBuffer: number;
  assignedCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateShiftRequest {
  name: string;
  startTime: string;
  endTime: string;
  checkInOpenMinutes: number;
  lateInThreshold: number;
  earlyOutThreshold: number;
  halfDayThreshold: number;
  otBuffer: number;
}

// Valid IP DTOs — Story 2.2
export type IpScope = 'COMPANY' | 'INDIVIDUAL';

export interface ValidIpDto {
  id: number;
  ipAddress: string;
  scope: IpScope;
  employeeId: string | null;
  employeeName: string | null;
  description: string | null;
  createdAt: string;
}

export interface CreateValidIpRequest {
  ipAddress: string;
  scope: IpScope;
  employeeId?: string;
  description?: string;
}

export interface EmployeeDto {
  id: string;
  fullName: string;
  username: string;
}

// Holiday DTOs — Story 2.3
export type HolidayType = 'FIXED' | 'DYNAMIC';

export interface HolidayDto {
  id: number;
  date: string;
  name: string;
  type: HolidayType;
  year: number;
  createdAt: string;
}

export interface CreateHolidayRequest {
  date: string;
  name: string;
  type: HolidayType;
  year: number;
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
