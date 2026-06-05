export enum UserRole {
  EMPLOYEE = 'EMPLOYEE',
  LEADER = 'LEADER',
  ADMIN = 'ADMIN',
}

export enum AttendanceStatus {
  ON_TIME = 'ON_TIME',
  LATE_IN = 'LATE_IN',
  EARLY_OUT = 'EARLY_OUT',
  LATE_IN_EARLY_OUT = 'LATE_IN_EARLY_OUT',
  HALF_DAY = 'HALF_DAY',
  INCOMPLETE = 'INCOMPLETE',
  ABSENT = 'ABSENT',
}

export enum ApprovalSubStatus {
  PENDING_APPROVAL = 'PENDING_APPROVAL',
  PENDING_ADJUSTMENT = 'PENDING_ADJUSTMENT',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
  ADMIN_OVERRIDE = 'ADMIN_OVERRIDE',
}

export enum RequestType {
  LATE_IN = 'LATE_IN',
  EARLY_OUT = 'EARLY_OUT',
  HALF_DAY = 'HALF_DAY',
  LATE_IN_EARLY_OUT = 'LATE_IN_EARLY_OUT',
}

export enum DayType {
  WEEKDAY = 'WEEKDAY',
  WEEKEND = 'WEEKEND',
  HOLIDAY = 'HOLIDAY',
}

export enum IpScope {
  COMPANY = 'COMPANY',
  INDIVIDUAL = 'INDIVIDUAL',
}

export enum HolidayType {
  FIXED = 'FIXED',
  DYNAMIC = 'DYNAMIC',
}

export const ATTENDANCE_STATUS_COLORS: Record<AttendanceStatus, string> = {
  [AttendanceStatus.ON_TIME]: 'bg-green-100 text-green-800',
  [AttendanceStatus.LATE_IN]: 'bg-amber-100 text-amber-800',
  [AttendanceStatus.EARLY_OUT]: 'bg-amber-100 text-amber-800',
  [AttendanceStatus.LATE_IN_EARLY_OUT]: 'bg-amber-100 text-amber-800',
  [AttendanceStatus.HALF_DAY]: 'bg-red-100 text-red-800',
  [AttendanceStatus.INCOMPLETE]: 'bg-red-100 text-red-800',
  [AttendanceStatus.ABSENT]: 'bg-slate-100 text-slate-800',
};
