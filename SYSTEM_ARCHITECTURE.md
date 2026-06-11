# ITX Attendance System - Architecture & Workflow

## 📊 System Overview

The ITX attendance system is a role-based application with three distinct user roles, each with specific responsibilities and permissions.

---

## 🔐 User Roles & Permissions

### 1. **EMPLOYEE** 👤
- **Primary Action**: Check in/out on-site using IP validation and camera verification
- **Requests**: Submit exception or adjustment requests for attendance discrepancies
- **View**: Personal attendance history and request status
- **Interactions**: 
  - Check in/out at work
  - View historical attendance records
  - Submit requests to their direct leader for approval
  - Receive notifications when requests are approved/rejected

### 2. **LEADER** 👥 (Team Manager/Supervisor)
- **Primary Action**: Review and approve/reject employee requests
- **Visibility**: Only sees requests from their direct reports
- **Responsibilities**:
  - Approve/reject exception requests (late arrivals, early departures, half-day)
  - Approve/reject adjustment requests (modify checkout times for incomplete records)
  - View team roster and daily attendance status
  - Send notifications to employees about request decisions

**Data Access Pattern**: Leaders are assigned to employees via `User.leader_id` field. When a leader views requests, the system queries only requests from their team members.

### 3. **ADMIN** 🛡️
- **Primary Action**: System configuration and oversight
- **Full Access**: Can see ALL requests, attendance records, and audit logs
- **Responsibilities**:
  - Manage shifts and assign to employees
  - Configure valid IP addresses for check-in validation
  - Manage holidays and special days
  - Override attendance records when needed (with immutable audit trail)
  - View all requests across all employees
  - Access audit logs for compliance and investigation
  - No team filtering - sees complete system state

**Data Access Pattern**: Admins have NO filtering - they see all records in the system.

---

## 📋 Request Management System

### Request Types

#### 1. **Exception Request**
Used when an employee's attendance doesn't match shift requirements:
- **Late In**: Arrived after shift start time
- **Early Out**: Left before shift end time
- **Half Day**: Marked absent but worked part of the shift
- **Late In + Early Out**: Both conditions apply

**Approval Impact**: Updates the attendance status in the system to reflect the exception.

#### 2. **Adjustment Request**
Used when employee needs to update their checkout time:
- Only for INCOMPLETE attendance records (missing checkout)
- Employee proposes a checkout time
- Leader approves the proposed time
- System recalculates OT (overtime) if applicable

**Approval Impact**: Sets the actual checkout time and recalculates attendance status.

---

## 🔄 Request Workflow

```
┌─────────────────┐
│    EMPLOYEE     │
│  Submits        │
│  Exception or   │
│ Adjustment Req  │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────┐
│   RequestService                │
│   - Validates request           │
│   - Checks limits (max 5        │
│     pending per employee)       │
│   - Creates request record      │
│   - Updates attendance status   │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│  LEADER or ADMIN Views          │
│  /api/requests/pending          │
│                                 │
│  LEADER: Sees only their        │
│           employees' requests   │
│  ADMIN:  Sees ALL requests      │
│           in system             │
└────────┬────────────────────────┘
         │
    ┌────┴─────┐
    │           │
    ▼           ▼
┌────────┐  ┌────────┐
│APPROVE │  │ REJECT │
└────┬───┘  └───┬────┘
     │          │
     ▼          ▼
┌──────────────────────────────────┐
│  Update Request Status           │
│  Update Attendance Record        │
│  Send Notification to Employee   │
│  Create Audit Log Entry          │
└──────────────────────────────────┘
```

---

## 🔗 Data Relationships

### User Hierarchy
```
ADMIN
  ├─ No team assigned
  └─ Sees all records

LEADER (Employee with role=LEADER)
  ├─ Has employees assigned via employee.leader_id
  └─ Sees only their team's requests

EMPLOYEE
  ├─ Assigned to a LEADER via employee.leader_id
  ├─ Assigned to a SHIFT
  └─ Creates requests and attendance records
```

### Request Relationships
```
User (EMPLOYEE)
  ├─ submits ExceptionRequest
  │   ├─ references AttendanceRecord
  │   └─ reviewed by User (LEADER/ADMIN)
  │
  └─ submits AdjustmentRequest
      ├─ references AttendanceRecord
      └─ reviewed by User (LEADER/ADMIN)
```

---

## 🔍 How the Backend Handles Different Roles

### RequestService.getPendingRequests(User currentUser)

```java
if (currentUser.getRole() == UserRole.ADMIN) {
    // ADMIN gets ALL pending requests
    exceptions = exceptionRequestRepository.findByStatus(PENDING);
    adjustments = adjustmentRequestRepository.findByStatus(PENDING);
} else if (currentUser.getRole() == UserRole.LEADER) {
    // LEADER gets only requests from their employees
    employeeIds = userRepository.findByLeaderId(currentUser.getId())
                               .stream()
                               .map(User::getId)
                               .toList();
    exceptions = exceptionRequestRepository.findByEmployeeIdInAndStatus(
        employeeIds, PENDING);
    adjustments = adjustmentRequestRepository.findByEmployeeIdInAndStatus(
        employeeIds, PENDING);
}
```

**Key Point**: The same endpoint `/api/requests/pending` returns different data based on the current user's role.

---

## 🎯 Frontend Implementation

### Why Admin & Leader Pages Use Same Component

Both ADMIN and LEADER roles use the `PendingRequestsScreen` component because:

1. **Same UI Logic**: Both need to display request lists with tabs (Pending/Approved/Rejected)
2. **Same API Endpoint**: Both call `/api/requests/pending` 
3. **Role-Based Filtering Happens on Backend**: The server returns appropriate data based on user role
4. **No Frontend-Side Filtering Needed**: Admin sees all requests, Leader sees only team requests

### Frontend Query Keys

```typescript
// Used by both Leader and Admin
queryKey: ['leader', 'pending-requests']
queryFn: requestService.getPending  // Calls /api/requests/pending
```

The component doesn't know if it's showing leader data or admin data - it just displays what the API returns.

---

## 🔐 Authorization Controls

### Backend Checks (`@PreAuthorize`)

1. **Request Submission**: `@PreAuthorize("hasRole('EMPLOYEE')")`
   - Only employees can submit requests

2. **Request Review**: `@PreAuthorize("hasAnyRole('LEADER', 'ADMIN')")`
   - Both leaders and admins can view/approve/reject

3. **System Configuration**: `@PreAuthorize("hasRole('ADMIN')")`
   - Only admins can manage shifts, IPs, holidays, etc.

### Business Logic Checks

**Leader Authorization** (RequestService.checkLeaderAuthorization):
```java
if (reviewer.getRole() == UserRole.ADMIN) return;  // Admins have full access
User employeeLeader = requestEmployee.getLeader();
if (employeeLeader == null || !employeeLeader.getId().equals(reviewer.getId())) {
    throw new BusinessException("Not authorized");
}
```

This ensures:
- ADMINs can approve/reject ANY request
- LEADERs can only approve/reject requests from their direct reports
- LEADERs cannot approve requests from other leaders' employees

---

## 🗂️ Database Tables Involved

### Users Table
- `role` (ENUM: EMPLOYEE, LEADER, ADMIN)
- `leader_id` (FK to User - who supervises this employee)
- `shift_id` (FK to Shift - assigned work shift)

### Exception Requests Table
- `employee_id` (FK to User)
- `reviewed_by` (FK to User - who approved/rejected)
- `status` (ENUM: PENDING, APPROVED, REJECTED)
- `request_type` (ENUM: LATE_IN, EARLY_OUT, HALF_DAY, LATE_IN_EARLY_OUT)

### Adjustment Requests Table
- `employee_id` (FK to User)
- `reviewed_by` (FK to User - who approved/rejected)
- `status` (ENUM: PENDING, APPROVED, REJECTED)
- `proposed_checkout_time` (datetime)

### Attendance Records Table
- `employee_id` (FK to User)
- `check_in_time` (datetime)
- `check_out_time` (datetime)
- `attendance_status` (ENUM: ON_TIME, LATE_IN, EARLY_OUT, HALF_DAY, INCOMPLETE, ABSENT)
- `approval_sub_status` (ENUM: PENDING_APPROVAL, PENDING_ADJUSTMENT, APPROVED, REJECTED)

---

## 🐛 Issue Fixed in This PR

### Problem
Admin RequestsPage was just a placeholder showing "Implement trong Story 4.x"

### Root Cause
The page component wasn't implemented, even though:
- Backend already supported admins viewing all requests
- Frontend had a reusable component (PendingRequestsScreen)
- RequestService backend logic handled both roles correctly

### Solution
- Implemented Admin RequestsPage to use the same `PendingRequestsScreen` component
- Removed placeholder text
- Now admins see all pending requests across the entire system

### How It Works Now
1. Admin navigates to Admin > Yêu cầu (Requests)
2. Frontend calls `/api/requests/pending` with admin authentication
3. Backend RequestService.getPendingRequests() detects ADMIN role
4. Returns ALL pending requests from ALL employees (no filtering)
5. UI displays requests in tabbed interface (Pending/Approved/Rejected)
6. Admin can click any request to view details and approve/reject

---

## 🔄 Why Admin Sees All Requests

**By Design**: 
- Admin is a system administrator, not a team manager
- Admins need visibility into ALL attendance issues for:
  - Compliance and audit purposes
  - Exception handling when leaders are unavailable
  - System-wide attendance troubleshooting
  - Override capabilities with immutable audit trail

**Comparison**:
- **LEADER**: Manages their team → Sees only team's requests
- **ADMIN**: Manages system → Sees ALL requests for oversight

---

## 📝 Future Enhancements

1. Admin Dashboard: Statistics on request approval rates, common exceptions
2. Bulk Actions: Admins could approve/reject multiple requests at once
3. Delegation: Allow leaders to reassign reviews to other leaders
4. Request Filtering: Admin could filter requests by date range, type, status
5. Escalation: Requests could escalate to admin if leader doesn't act within timeframe

---

## 🚀 Getting Started

### For Developers
1. Understand role-based architecture before making changes
2. Always check `@PreAuthorize` annotations
3. Remember: Frontend gets filtered data from backend based on role
4. Test with multiple user roles (EMPLOYEE, LEADER, ADMIN)

### For Admins
1. Log in with ADMIN account
2. Go to Admin > Yêu cầu to see all requests system-wide
3. Use tabs to filter by status (Pending/Approved/Rejected)
4. Click any request to view details and take action
5. All actions are logged in audit trail
