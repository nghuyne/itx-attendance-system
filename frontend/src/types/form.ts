import type { RequestType } from './domain';

// Login form — Story 1.5
export interface LoginFormValues {
  username: string;
  password: string;
}

// Exception request form — Story 4.1
export interface ExceptionRequestFormValues {
  requestType: RequestType;
  reason: string;
  date: string;
}

// Admin override form — Story 5.1
export interface AdminOverrideFormValues {
  userId: number;
  date: string;
  checkInTime: string;
  checkOutTime: string;
  reason: string;
}
