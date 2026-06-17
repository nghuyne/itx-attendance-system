package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.dto.request.AdjustmentRequestCreateDto;
import com.itx.attendance.dto.request.ExceptionRequestCreateDto;
import com.itx.attendance.dto.request.LeaveRequestCreateDto;
import com.itx.attendance.dto.response.AdjustmentRequestDto;
import com.itx.attendance.dto.response.ExceptionRequestDto;
import com.itx.attendance.dto.response.LeaveBalanceDto;
import com.itx.attendance.dto.response.LeaveRequestDto;
import com.itx.attendance.dto.response.RequestSummaryDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.AdjustmentRequestRepository;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.ExceptionRequestRepository;
import com.itx.attendance.repository.LeaveBalanceRepository;
import com.itx.attendance.repository.LeaveRequestRepository;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import com.itx.attendance.util.TimeUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RequestService {

    private final ExceptionRequestRepository exceptionRequestRepository;
    private final AdjustmentRequestRepository adjustmentRequestRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AttendanceService attendanceService;
    private final OtCalculationService otCalculationService;
    private final HolidayService holidayService;

    public ExceptionRequestDto submitExceptionRequest(ExceptionRequestCreateDto request) {
        String username = SecurityUtil.getCurrentUsername();
        if (username == null) {
            throw new BusinessException(
                "Security context not found",
                HttpStatus.UNAUTHORIZED, "SECURITY_CONTEXT_MISSING");
        }
        User employee = userRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException(
                "Employee not found",
                HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND"));

        AttendanceRecord record = attendanceRecordRepository.findById(request.attendanceRecordId())
            .orElseThrow(() -> new BusinessException(
                "Attendance record not found",
                HttpStatus.NOT_FOUND, "ATTENDANCE_RECORD_NOT_FOUND"));

        if (record.getEmployee() == null) {
            throw new BusinessException(
                "Record has no associated employee",
                HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_RECORD_STATE");
        }
        if (!record.getEmployee().getId().equals(employee.getId())) {
            throw new BusinessException(
                "Unauthorized: record does not belong to current employee",
                HttpStatus.FORBIDDEN, "UNAUTHORIZED_RECORD_ACCESS");
        }

        AttendanceStatus status = record.getAttendanceStatus();
        if (status == null) {
            throw new BusinessException(
                "Attendance status not set",
                HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_RECORD_STATE");
        }
        if (!isValidExceptionRequestStatus(status)) {
            throw new BusinessException(
                "Cannot submit exception request for status: " + status,
                HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_STATUS");
        }

        long pendingCount = exceptionRequestRepository.countByEmployeeIdAndStatus(employee.getId(), RequestStatus.PENDING) +
                           adjustmentRequestRepository.countByEmployeeIdAndStatus(employee.getId(), RequestStatus.PENDING);
        if (pendingCount >= 5) {
            throw new BusinessException(
                "Maximum 5 pending requests allowed per employee",
                HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_PENDING_REQUESTS");
        }

        if (exceptionRequestRepository.findByAttendanceRecordIdAndStatus(
                request.attendanceRecordId(), RequestStatus.PENDING).isPresent()) {
            throw new BusinessException(
                "A pending exception request already exists for this record",
                HttpStatus.CONFLICT, "PENDING_REQUEST_EXISTS");
        }

        ExceptionRequest exceptionRequest = ExceptionRequest.builder()
            .attendanceRecord(record)
            .employee(employee)
            .requestType(request.requestType())
            .reason(request.reason())
            .status(RequestStatus.PENDING)
            .build();

        ExceptionRequest savedRequest;
        try {
            savedRequest = exceptionRequestRepository.save(exceptionRequest);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                "A pending exception request already exists for this record",
                HttpStatus.CONFLICT, "PENDING_REQUEST_EXISTS");
        }

        record.setApprovalSubStatus(ApprovalSubStatus.PENDING_APPROVAL);
        attendanceRecordRepository.save(record);

        notificationService.sendExceptionRequestNotification(savedRequest);

        log.info("Exception request created: id={}, recordId={}, type={}",
            savedRequest.getId(), record.getId(), request.requestType());

        return toExceptionRequestDto(savedRequest);
    }

    public AdjustmentRequestDto submitAdjustmentRequest(AdjustmentRequestCreateDto request) {
        String username = SecurityUtil.getCurrentUsername();
        if (username == null) {
            throw new BusinessException(
                "Security context not found",
                HttpStatus.UNAUTHORIZED, "SECURITY_CONTEXT_MISSING");
        }
        User employee = userRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException(
                "Employee not found",
                HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND"));

        AttendanceRecord record = attendanceRecordRepository.findById(request.attendanceRecordId())
            .orElseThrow(() -> new BusinessException(
                "Attendance record not found",
                HttpStatus.NOT_FOUND, "ATTENDANCE_RECORD_NOT_FOUND"));

        if (record.getEmployee() == null) {
            throw new BusinessException(
                "Record has no associated employee",
                HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_RECORD_STATE");
        }
        if (!record.getEmployee().getId().equals(employee.getId())) {
            throw new BusinessException(
                "Unauthorized: record does not belong to current employee",
                HttpStatus.FORBIDDEN, "UNAUTHORIZED_RECORD_ACCESS");
        }

        if (record.getAttendanceStatus() != AttendanceStatus.INCOMPLETE) {
            throw new BusinessException(
                "Adjustment request can only be submitted for INCOMPLETE records",
                HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_STATUS");
        }

        if (record.getCheckInTime() == null) {
            throw new BusinessException(
                "Attendance record has no check-in time",
                HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_RECORD_STATE");
        }
        if (request.proposedCheckoutTime().isBefore(record.getCheckInTime().toInstant(ZoneOffset.UTC))) {
            throw new BusinessException(
                "Proposed checkout time must be after check-in time",
                HttpStatus.BAD_REQUEST, "INVALID_CHECKOUT_TIME");
        }

        if (record.getShift() != null && record.getShift().getShiftEndTime() != null) {
            // shiftEndTime is VN local time (UTC+7); convert to UTC Instant for comparison
            Instant shiftEndUtc = record.getShift().getShiftEndTime()
                .atDate(record.getDate())
                .atZone(TimeUtil.UTC_PLUS_7)
                .toInstant();
            if (request.proposedCheckoutTime().isAfter(shiftEndUtc.plusSeconds(2 * 3600L))) {
                throw new BusinessException(
                    "Proposed checkout time is unreasonable (too far beyond shift end)",
                    HttpStatus.BAD_REQUEST, "INVALID_CHECKOUT_TIME");
            }
        }

        long pendingCount = exceptionRequestRepository.countByEmployeeIdAndStatus(employee.getId(), RequestStatus.PENDING) +
                           adjustmentRequestRepository.countByEmployeeIdAndStatus(employee.getId(), RequestStatus.PENDING);
        if (pendingCount >= 5) {
            throw new BusinessException(
                "Maximum 5 pending requests allowed per employee",
                HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_PENDING_REQUESTS");
        }

        if (adjustmentRequestRepository.findByAttendanceRecordIdAndStatus(
                request.attendanceRecordId(), RequestStatus.PENDING).isPresent()) {
            throw new BusinessException(
                "A pending adjustment request already exists for this record",
                HttpStatus.CONFLICT, "PENDING_REQUEST_EXISTS");
        }

        AdjustmentRequest adjustmentRequest = AdjustmentRequest.builder()
            .attendanceRecord(record)
            .employee(employee)
            .proposedCheckoutTime(request.proposedCheckoutTime())
            .reason(request.reason())
            .status(RequestStatus.PENDING)
            .build();

        AdjustmentRequest savedRequest;
        try {
            savedRequest = adjustmentRequestRepository.save(adjustmentRequest);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                "A pending adjustment request already exists for this record",
                HttpStatus.CONFLICT, "PENDING_REQUEST_EXISTS");
        }

        record.setApprovalSubStatus(ApprovalSubStatus.PENDING_ADJUSTMENT);
        attendanceRecordRepository.save(record);

        notificationService.sendAdjustmentRequestNotification(savedRequest);

        log.info("Adjustment request created: id={}, recordId={}, proposedCheckoutTime={}",
            savedRequest.getId(), record.getId(), request.proposedCheckoutTime());

        return toAdjustmentRequestDto(savedRequest);
    }

    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 200)
    )
    public RequestSummaryDto approveRequest(String requestId, User reviewer) {
        Optional<ExceptionRequest> exOpt = exceptionRequestRepository.findByIdForUpdate(requestId);
        if (exOpt.isPresent()) {
            return approveExceptionRequest(exOpt.get(), reviewer);
        }
        Optional<AdjustmentRequest> adjOpt = adjustmentRequestRepository.findByIdForUpdate(requestId);
        if (adjOpt.isPresent()) {
            return approveAdjustmentRequest(adjOpt.get(), reviewer);
        }
        try {
            Long leaveId = Long.parseLong(requestId);
            Optional<LeaveRequest> leaveOpt = leaveRequestRepository.findByIdForUpdate(leaveId);
            if (leaveOpt.isPresent()) {
                return approveLeaveRequest(leaveOpt.get(), reviewer);
            }
        } catch (NumberFormatException ignored) {}
        throw new BusinessException("Request not found", HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND");
    }

    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 200)
    )
    public RequestSummaryDto rejectRequest(String requestId, String reason, User reviewer) {
        Optional<ExceptionRequest> exOpt = exceptionRequestRepository.findByIdForUpdate(requestId);
        if (exOpt.isPresent()) {
            return rejectExceptionRequest(exOpt.get(), reason, reviewer);
        }
        Optional<AdjustmentRequest> adjOpt = adjustmentRequestRepository.findByIdForUpdate(requestId);
        if (adjOpt.isPresent()) {
            return rejectAdjustmentRequest(adjOpt.get(), reason, reviewer);
        }
        try {
            Long leaveId = Long.parseLong(requestId);
            Optional<LeaveRequest> leaveOpt = leaveRequestRepository.findByIdForUpdate(leaveId);
            if (leaveOpt.isPresent()) {
                return rejectLeaveRequest(leaveOpt.get(), reason, reviewer);
            }
        } catch (NumberFormatException ignored) {}
        throw new BusinessException("Request not found", HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND");
    }

    public List<RequestSummaryDto> getMyRequests(User employee) {
        List<ExceptionRequest> exceptions = exceptionRequestRepository.findByEmployeeId(employee.getId());
        List<AdjustmentRequest> adjustments = adjustmentRequestRepository.findByEmployeeId(employee.getId());
        List<LeaveRequest> leaves = leaveRequestRepository.findByEmployeeId(employee.getId());

        List<RequestSummaryDto> result = new ArrayList<>();
        exceptions.forEach(e -> result.add(toRequestSummaryDto(e)));
        adjustments.forEach(a -> result.add(toRequestSummaryDto(a)));
        leaves.forEach(l -> result.add(toRequestSummaryDto(l)));
        result.sort(Comparator.comparing(RequestSummaryDto::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return result;
    }

    public List<RequestSummaryDto> getPendingRequests(User currentUser) {
        List<ExceptionRequest> exceptions;
        List<AdjustmentRequest> adjustments;
        List<LeaveRequest> leaves;

        if (currentUser.getRole() == UserRole.ADMIN) {
            exceptions = exceptionRequestRepository.findByStatus(RequestStatus.PENDING);
            adjustments = adjustmentRequestRepository.findByStatus(RequestStatus.PENDING);
            leaves = leaveRequestRepository.findByStatus(RequestStatus.PENDING);
        } else {
            List<String> employeeIds = userRepository.findByLeaderId(currentUser.getId())
                .stream().map(User::getId).toList();
            if (employeeIds.isEmpty()) {
                return List.of();
            }
            exceptions = exceptionRequestRepository.findByEmployeeIdInAndStatus(employeeIds, RequestStatus.PENDING);
            adjustments = adjustmentRequestRepository.findByEmployeeIdInAndStatus(employeeIds, RequestStatus.PENDING);
            leaves = leaveRequestRepository.findByEmployeeIdInAndStatus(employeeIds, RequestStatus.PENDING);
        }

        List<RequestSummaryDto> result = new ArrayList<>();
        exceptions.forEach(e -> result.add(toRequestSummaryDto(e)));
        adjustments.forEach(a -> result.add(toRequestSummaryDto(a)));
        leaves.forEach(l -> result.add(toRequestSummaryDto(l)));
        result.sort(Comparator.comparing(RequestSummaryDto::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return result;
    }

    private RequestSummaryDto approveExceptionRequest(ExceptionRequest request, User reviewer) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Request is not pending", HttpStatus.BAD_REQUEST, "REQUEST_NOT_PENDING");
        }
        checkLeaderAuthorization(reviewer, request.getEmployee());

        request.setStatus(RequestStatus.APPROVED);
        request.setReviewedBy(reviewer);
        exceptionRequestRepository.save(request);

        AttendanceRecord record = request.getAttendanceRecord();
        record.setAttendanceStatus(AttendanceStatus.EXCUSED);
        record.setApprovalSubStatus(ApprovalSubStatus.APPROVED);
        attendanceRecordRepository.saveAndFlush(record);

        notificationService.sendRequestApprovedNotification(
            request.getEmployee(), request.getId(), "ngoại lệ");

        log.info("Exception request approved: id={}, reviewer={}", request.getId(), reviewer.getId());
        return toRequestSummaryDto(request);
    }

    private RequestSummaryDto approveAdjustmentRequest(AdjustmentRequest request, User reviewer) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Request is not pending", HttpStatus.BAD_REQUEST, "REQUEST_NOT_PENDING");
        }
        checkLeaderAuthorization(reviewer, request.getEmployee());

        AttendanceRecord record = request.getAttendanceRecord();
        if (record.getCheckInTime() == null) {
            throw new BusinessException("Attendance record has no check-in time", HttpStatus.BAD_REQUEST, "INVALID_RECORD_STATE");
        }
        if (record.getShift() == null) {
            throw new BusinessException("Attendance record has no shift assigned", HttpStatus.BAD_REQUEST, "INVALID_RECORD_STATE");
        }

        request.setStatus(RequestStatus.APPROVED);
        request.setReviewedBy(reviewer);
        adjustmentRequestRepository.save(request);

        LocalDateTime proposedCheckoutUtc = LocalDateTime.ofInstant(
            request.getProposedCheckoutTime(), ZoneOffset.UTC);
        record.setCheckOutTime(proposedCheckoutUtc);
        record.setAttendanceStatus(attendanceService.computeFinalStatus(
            record.getCheckInTime(), proposedCheckoutUtc, record.getShift()));
        record.setApprovalSubStatus(ApprovalSubStatus.APPROVED);
        attendanceRecordRepository.saveAndFlush(record);

        if (record.getAttendanceStatus() != AttendanceStatus.INCOMPLETE
                && record.getAttendanceStatus() != AttendanceStatus.ABSENT) {
            otCalculationService.recalculate(record);
        }

        notificationService.sendRequestApprovedNotification(
            request.getEmployee(), request.getId(), "điều chỉnh");

        log.info("Adjustment request approved: id={}, reviewer={}", request.getId(), reviewer.getId());
        return toRequestSummaryDto(request);
    }

    private RequestSummaryDto rejectExceptionRequest(ExceptionRequest request, String reason, User reviewer) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Request is not pending", HttpStatus.BAD_REQUEST, "REQUEST_NOT_PENDING");
        }
        checkLeaderAuthorization(reviewer, request.getEmployee());

        request.setStatus(RequestStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewReason(reason);
        exceptionRequestRepository.save(request);

        AttendanceRecord record = request.getAttendanceRecord();
        record.setApprovalSubStatus(ApprovalSubStatus.REJECTED);
        attendanceRecordRepository.saveAndFlush(record);

        notificationService.sendRequestRejectedNotification(
            request.getEmployee(), request.getId(), "ngoại lệ " + request.getRequestType(), reason);

        log.info("Exception request rejected: id={}, reviewer={}", request.getId(), reviewer.getId());
        return toRequestSummaryDto(request);
    }

    private RequestSummaryDto rejectAdjustmentRequest(AdjustmentRequest request, String reason, User reviewer) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Request is not pending", HttpStatus.BAD_REQUEST, "REQUEST_NOT_PENDING");
        }
        checkLeaderAuthorization(reviewer, request.getEmployee());

        request.setStatus(RequestStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewReason(reason);
        adjustmentRequestRepository.save(request);

        AttendanceRecord record = request.getAttendanceRecord();
        record.setApprovalSubStatus(ApprovalSubStatus.REJECTED);
        attendanceRecordRepository.saveAndFlush(record);

        notificationService.sendRequestRejectedNotification(
            request.getEmployee(), request.getId(), "điều chỉnh", reason);

        log.info("Adjustment request rejected: id={}, reviewer={}", request.getId(), reviewer.getId());
        return toRequestSummaryDto(request);
    }

    private void checkLeaderAuthorization(User reviewer, User requestEmployee) {
        if (reviewer.getRole() == UserRole.ADMIN) return;
        User employeeLeader = requestEmployee.getLeader();
        if (employeeLeader == null || !employeeLeader.getId().equals(reviewer.getId())) {
            throw new BusinessException(
                "Leader not authorized for this employee's request",
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
    }

    public List<RequestSummaryDto> getRequestsByStatus(User currentUser, RequestStatus status) {
        List<ExceptionRequest> exceptions;
        List<AdjustmentRequest> adjustments;
        List<LeaveRequest> leaves;

        if (currentUser.getRole() == UserRole.ADMIN) {
            exceptions = exceptionRequestRepository.findByStatus(status);
            adjustments = adjustmentRequestRepository.findByStatus(status);
            leaves = leaveRequestRepository.findByStatus(status);
        } else {
            List<String> employeeIds = userRepository.findByLeaderId(currentUser.getId())
                .stream().map(User::getId).toList();
            if (employeeIds.isEmpty()) return List.of();
            exceptions = exceptionRequestRepository.findByEmployeeIdInAndStatus(employeeIds, status);
            adjustments = adjustmentRequestRepository.findByEmployeeIdInAndStatus(employeeIds, status);
            leaves = leaveRequestRepository.findByEmployeeIdInAndStatus(employeeIds, status);
        }

        List<RequestSummaryDto> result = new ArrayList<>();
        exceptions.forEach(e -> result.add(toRequestSummaryDto(e)));
        adjustments.forEach(a -> result.add(toRequestSummaryDto(a)));
        leaves.forEach(l -> result.add(toRequestSummaryDto(l)));
        result.sort(Comparator.comparing(RequestSummaryDto::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return result;
    }

    public LeaveRequestDto submitLeaveRequest(LeaveRequestCreateDto request) {
        String username = SecurityUtil.getCurrentUsername();
        if (username == null) {
            throw new BusinessException("Security context not found", HttpStatus.UNAUTHORIZED, "SECURITY_CONTEXT_MISSING");
        }
        User employee = userRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException("Employee not found", HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND"));

        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();

        if (startDate.isBefore(LocalDate.now())) {
            throw new BusinessException("Start date cannot be in the past", HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("Start date must be before or equal to end date", HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
        }

        int totalDays = holidayService.countBusinessDays(startDate, endDate);
        if (totalDays == 0) {
            throw new BusinessException("No business days in the selected range", HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
        }

        if (leaveRequestRepository.countOverlapping(employee.getId(), startDate, endDate) > 0) {
            throw new BusinessException("Leave dates overlap with an existing request", HttpStatus.CONFLICT, "LEAVE_DATE_CONFLICT");
        }

        LeaveBalance balance = getOrCreateBalance(employee, startDate.getYear(), request.leaveType());
        int remaining = balance.getTotalDays() - balance.getUsedDays();
        if (remaining < totalDays) {
            throw new BusinessException("Quỹ phép không đủ", HttpStatus.BAD_REQUEST, "INSUFFICIENT_LEAVE_BALANCE");
        }

        LeaveRequest leaveRequest = LeaveRequest.builder()
            .employee(employee)
            .leaveType(request.leaveType())
            .startDate(startDate)
            .endDate(endDate)
            .totalDays(totalDays)
            .reason(request.reason())
            .status(RequestStatus.PENDING)
            .build();

        LeaveRequest saved;
        try {
            saved = leaveRequestRepository.save(leaveRequest);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Leave dates overlap with an existing request", HttpStatus.CONFLICT, "LEAVE_DATE_CONFLICT");
        }

        notificationService.sendLeaveRequestNotification(saved);

        log.info("Leave request created: id={}, employee={}, type={}, days={}", saved.getId(), employee.getId(), request.leaveType(), totalDays);

        return toLeaveRequestDto(saved);
    }

    private RequestSummaryDto approveLeaveRequest(LeaveRequest request, User reviewer) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Request is not pending", HttpStatus.BAD_REQUEST, "REQUEST_NOT_PENDING");
        }
        checkLeaderAuthorization(reviewer, request.getEmployee());

        request.setStatus(RequestStatus.APPROVED);
        request.setApprover(reviewer);
        leaveRequestRepository.save(request);

        LeaveBalance balance = getOrCreateBalance(request.getEmployee(), request.getStartDate().getYear(), request.getLeaveType());
        balance.setUsedDays(balance.getUsedDays() + request.getTotalDays());
        leaveBalanceRepository.save(balance);

        notificationService.sendRequestApprovedNotification(
            request.getEmployee(), String.valueOf(request.getId()), "nghỉ phép");

        log.info("Leave request approved: id={}, reviewer={}", request.getId(), reviewer.getId());
        return toRequestSummaryDto(request);
    }

    private RequestSummaryDto rejectLeaveRequest(LeaveRequest request, String reason, User reviewer) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Request is not pending", HttpStatus.BAD_REQUEST, "REQUEST_NOT_PENDING");
        }
        checkLeaderAuthorization(reviewer, request.getEmployee());

        request.setStatus(RequestStatus.REJECTED);
        request.setApprover(reviewer);
        request.setRejectionReason(reason);
        leaveRequestRepository.save(request);

        notificationService.sendRequestRejectedNotification(
            request.getEmployee(), String.valueOf(request.getId()), "nghỉ phép", reason);

        log.info("Leave request rejected: id={}, reviewer={}", request.getId(), reviewer.getId());
        return toRequestSummaryDto(request);
    }

    public List<LeaveBalanceDto> getLeaveBalance() {
        String username = SecurityUtil.getCurrentUsername();
        if (username == null) {
            throw new BusinessException("Security context not found", HttpStatus.UNAUTHORIZED, "SECURITY_CONTEXT_MISSING");
        }
        User employee = userRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException("Employee not found", HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND"));

        int year = LocalDate.now().getYear();
        List<LeaveBalance> balances = leaveBalanceRepository.findByEmployeeIdAndYear(employee.getId(), year);

        boolean hasAnnual = balances.stream().anyMatch(b -> b.getLeaveType() == LeaveType.ANNUAL);
        boolean hasSick = balances.stream().anyMatch(b -> b.getLeaveType() == LeaveType.SICK);
        if (!hasAnnual || !hasSick) {
            List<LeaveBalance> mutable = new ArrayList<>(balances);
            if (!hasAnnual) mutable.add(getOrCreateBalance(employee, year, LeaveType.ANNUAL));
            if (!hasSick) mutable.add(getOrCreateBalance(employee, year, LeaveType.SICK));
            balances = mutable;
        }

        return balances.stream().map(this::toLeaveBalanceDto).toList();
    }

    private LeaveBalance getOrCreateBalance(User employee, int year, LeaveType type) {
        return leaveBalanceRepository.findByEmployeeIdAndYearAndLeaveType(employee.getId(), year, type)
            .orElseGet(() -> {
                int defaultDays = type == LeaveType.ANNUAL ? 12 : 5;
                LeaveBalance balance = LeaveBalance.builder()
                    .employee(employee)
                    .year(year)
                    .leaveType(type)
                    .totalDays(defaultDays)
                    .usedDays(0)
                    .build();
                try {
                    return leaveBalanceRepository.save(balance);
                } catch (DataIntegrityViolationException e) {
                    return leaveBalanceRepository.findByEmployeeIdAndYearAndLeaveType(employee.getId(), year, type)
                        .orElseThrow(() -> new BusinessException("Balance creation failed", HttpStatus.INTERNAL_SERVER_ERROR, "BALANCE_ERROR"));
                }
            });
    }

    private LeaveRequestDto toLeaveRequestDto(LeaveRequest request) {
        return LeaveRequestDto.builder()
            .id(request.getId())
            .employeeId(request.getEmployee().getId())
            .leaveType(request.getLeaveType())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .totalDays(request.getTotalDays())
            .reason(request.getReason())
            .status(request.getStatus())
            .approverId(request.getApprover() != null ? request.getApprover().getId() : null)
            .rejectionReason(request.getRejectionReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .build();
    }

    private LeaveBalanceDto toLeaveBalanceDto(LeaveBalance balance) {
        return LeaveBalanceDto.builder()
            .id(balance.getId())
            .employeeId(balance.getEmployee().getId())
            .year(balance.getYear())
            .leaveType(balance.getLeaveType())
            .totalDays(balance.getTotalDays())
            .usedDays(balance.getUsedDays())
            .build();
    }

    private RequestSummaryDto toRequestSummaryDto(ExceptionRequest request) {
        AttendanceRecord record = request.getAttendanceRecord();
        return RequestSummaryDto.builder()
            .id(request.getId())
            .requestCategory("EXCEPTION")
            .employeeId(request.getEmployee().getId())
            .employeeName(request.getEmployee().getFullName())
            .attendanceRecordId(record.getId())
            .attendanceDate(record.getDate())
            .requestType(request.getRequestType())
            .proposedCheckoutTime(null)
            .checkInTime(record.getCheckInTime())
            .checkOutTime(record.getCheckOutTime())
            .reason(request.getReason())
            .status(request.getStatus())
            .reviewedBy(request.getReviewedBy() != null ? request.getReviewedBy().getId() : null)
            .reviewReason(request.getReviewReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .leaveType(null)
            .startDate(null)
            .endDate(null)
            .totalDays(null)
            .build();
    }

    private RequestSummaryDto toRequestSummaryDto(AdjustmentRequest request) {
        AttendanceRecord record = request.getAttendanceRecord();
        return RequestSummaryDto.builder()
            .id(request.getId())
            .requestCategory("ADJUSTMENT")
            .employeeId(request.getEmployee().getId())
            .employeeName(request.getEmployee().getFullName())
            .attendanceRecordId(record.getId())
            .attendanceDate(record.getDate())
            .requestType(null)
            .proposedCheckoutTime(request.getProposedCheckoutTime())
            .checkInTime(record.getCheckInTime())
            .checkOutTime(record.getCheckOutTime())
            .reason(request.getReason())
            .status(request.getStatus())
            .reviewedBy(request.getReviewedBy() != null ? request.getReviewedBy().getId() : null)
            .reviewReason(request.getReviewReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .leaveType(null)
            .startDate(null)
            .endDate(null)
            .totalDays(null)
            .build();
    }

    private RequestSummaryDto toRequestSummaryDto(LeaveRequest request) {
        return RequestSummaryDto.builder()
            .id(String.valueOf(request.getId()))
            .requestCategory("LEAVE")
            .employeeId(request.getEmployee().getId())
            .employeeName(request.getEmployee().getFullName())
            .attendanceRecordId(null)
            .attendanceDate(null)
            .requestType(null)
            .proposedCheckoutTime(null)
            .checkInTime(null)
            .checkOutTime(null)
            .reason(request.getReason())
            .status(request.getStatus())
            .reviewedBy(request.getApprover() != null ? request.getApprover().getId() : null)
            .reviewReason(request.getRejectionReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .leaveType(request.getLeaveType())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .totalDays(request.getTotalDays())
            .build();
    }

    private boolean isValidExceptionRequestStatus(AttendanceStatus status) {
        return status == AttendanceStatus.LATE_IN ||
               status == AttendanceStatus.EARLY_OUT ||
               status == AttendanceStatus.HALF_DAY ||
               status == AttendanceStatus.LATE_IN_EARLY_OUT;
    }

    private ExceptionRequestDto toExceptionRequestDto(ExceptionRequest request) {
        return ExceptionRequestDto.builder()
            .id(request.getId())
            .attendanceRecordId(request.getAttendanceRecord().getId())
            .employeeId(request.getEmployee().getId())
            .requestType(request.getRequestType())
            .reason(request.getReason())
            .status(request.getStatus())
            .reviewedBy(request.getReviewedBy() != null ? request.getReviewedBy().getId() : null)
            .reviewReason(request.getReviewReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .build();
    }

    private AdjustmentRequestDto toAdjustmentRequestDto(AdjustmentRequest request) {
        return AdjustmentRequestDto.builder()
            .id(request.getId())
            .attendanceRecordId(request.getAttendanceRecord().getId())
            .employeeId(request.getEmployee().getId())
            .proposedCheckoutTime(request.getProposedCheckoutTime())
            .reason(request.getReason())
            .status(request.getStatus())
            .reviewedBy(request.getReviewedBy() != null ? request.getReviewedBy().getId() : null)
            .reviewReason(request.getReviewReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .build();
    }
}
