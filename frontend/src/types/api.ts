import type { UserRole, AttendanceStatus, ApprovalSubStatus } from './domain';

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

// Attendance DTOs — Story 3.1+
export interface AttendanceRecordDto {
  id: string;
  employeeId: string;
  shiftId: string;
  shiftName: string;
  shiftStartTime: string;
  shiftEndTime: string;
  date: string;
  checkInTime: string | null;
  checkInIp: string | null;
  checkInLat: number | null;
  checkInLng: number | null;
  checkInPhotoUrl: string | null;
  checkOutTime: string | null;
  checkOutIp: string | null;
  checkOutLat: number | null;
  checkOutLng: number | null;
  checkOutPhotoUrl: string | null;
  attendanceStatus: AttendanceStatus;
  approvalSubStatus: ApprovalSubStatus | null;
  isClientSite: boolean;
  gpsUnavailable: boolean;
  suspiciousLocation: boolean;
  isAdminOverride: boolean;
  version: number;
  createdAt: string;
}

export interface CheckInRequest {
  lat: number | null;
  lng: number | null;
  photoBase64: string;
  isClientSite: boolean;
}

export interface CheckOutRequest {
  lat: number | null;
  lng: number | null;
  photoBase64: string;
}

// Request DTOs — Story 4.1
export type ExceptionRequestType = 'LATE_IN' | 'EARLY_OUT' | 'HALF_DAY' | 'LATE_IN_EARLY_OUT';
export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ExceptionRequestCreateDto {
  attendanceRecordId: string;
  requestType: ExceptionRequestType;
  reason: string;
}

export interface AdjustmentRequestCreateDto {
  attendanceRecordId: string;
  proposedCheckoutTime: string;
  reason: string;
}

export interface ExceptionRequestDto {
  id: string;
  attendanceRecordId: string;
  employeeId: string;
  requestType: ExceptionRequestType;
  reason: string;
  status: RequestStatus;
  reviewedBy: string | null;
  reviewReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdjustmentRequestDto {
  id: string;
  attendanceRecordId: string;
  employeeId: string;
  proposedCheckoutTime: string;
  reason: string;
  status: RequestStatus;
  reviewedBy: string | null;
  reviewReason: string | null;
  createdAt: string;
  updatedAt: string;
}

// Notification DTOs — Story 4.2
export type NotificationType = 'EXCEPTION_REQUEST' | 'ADJUSTMENT_REQUEST' | 'REQUEST_APPROVED' | 'REQUEST_REJECTED' | 'INCOMPLETE_RECORD';

export interface NotificationDto {
  id: string;
  recipientId: string;
  type: NotificationType;
  referenceId: string | null;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export interface NotificationPendingResponse {
  notifications: NotificationDto[];
  unreadCount: number;
}
