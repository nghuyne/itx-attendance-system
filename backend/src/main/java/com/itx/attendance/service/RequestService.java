package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.dto.request.AdjustmentRequestCreateDto;
import com.itx.attendance.dto.request.ExceptionRequestCreateDto;
import com.itx.attendance.dto.response.AdjustmentRequestDto;
import com.itx.attendance.dto.response.ExceptionRequestDto;
import com.itx.attendance.dto.response.RequestSummaryDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.AdjustmentRequestRepository;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.ExceptionRequestRepository;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AttendanceService attendanceService;
    private final OtCalculationService otCalculationService;

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
        if (request.proposedCheckoutTime().isBefore(record.getCheckInTime())) {
            throw new BusinessException(
                "Proposed checkout time must be after check-in time",
                HttpStatus.BAD_REQUEST, "INVALID_CHECKOUT_TIME");
        }

        if (record.getShift() != null && record.getShift().getShiftEndTime() != null) {
            LocalDateTime shiftEndTime = record.getShift().getShiftEndTime()
                .atDate(record.getDate());
            if (request.proposedCheckoutTime().isAfter(shiftEndTime.plusHours(2))) {
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

    public RequestSummaryDto approveRequest(String requestId, User reviewer) {
        Optional<ExceptionRequest> exOpt = exceptionRequestRepository.findById(requestId);
        if (exOpt.isPresent()) {
            return approveExceptionRequest(exOpt.get(), reviewer);
        }
        Optional<AdjustmentRequest> adjOpt = adjustmentRequestRepository.findById(requestId);
        if (adjOpt.isPresent()) {
            return approveAdjustmentRequest(adjOpt.get(), reviewer);
        }
        throw new BusinessException("Request not found", HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND");
    }

    public RequestSummaryDto rejectRequest(String requestId, String reason, User reviewer) {
        Optional<ExceptionRequest> exOpt = exceptionRequestRepository.findById(requestId);
        if (exOpt.isPresent()) {
            return rejectExceptionRequest(exOpt.get(), reason, reviewer);
        }
        Optional<AdjustmentRequest> adjOpt = adjustmentRequestRepository.findById(requestId);
        if (adjOpt.isPresent()) {
            return rejectAdjustmentRequest(adjOpt.get(), reason, reviewer);
        }
        throw new BusinessException("Request not found", HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND");
    }

    public List<RequestSummaryDto> getPendingRequests(User currentUser) {
        List<ExceptionRequest> exceptions;
        List<AdjustmentRequest> adjustments;

        if (currentUser.getRole() == UserRole.ADMIN) {
            exceptions = exceptionRequestRepository.findByStatus(RequestStatus.PENDING);
            adjustments = adjustmentRequestRepository.findByStatus(RequestStatus.PENDING);
        } else {
            List<String> employeeIds = userRepository.findByLeaderId(currentUser.getId())
                .stream().map(User::getId).toList();
            if (employeeIds.isEmpty()) {
                return List.of();
            }
            exceptions = exceptionRequestRepository.findByEmployeeIdInAndStatus(employeeIds, RequestStatus.PENDING);
            adjustments = adjustmentRequestRepository.findByEmployeeIdInAndStatus(employeeIds, RequestStatus.PENDING);
        }

        List<RequestSummaryDto> result = new ArrayList<>();
        exceptions.forEach(e -> result.add(toRequestSummaryDto(e)));
        adjustments.forEach(a -> result.add(toRequestSummaryDto(a)));
        result.sort(Comparator.comparing(RequestSummaryDto::createdAt).reversed());
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
        record.setApprovalSubStatus(ApprovalSubStatus.APPROVED);
        try {
            attendanceRecordRepository.save(record);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("Record was modified concurrently — please retry", HttpStatus.CONFLICT, "CONCURRENT_UPDATE");
        }

        notificationService.sendRequestApprovedNotification(
            request.getEmployee(), request.getId(),
            "ngoại lệ " + request.getRequestType());

        log.info("Exception request approved: id={}, reviewer={}", request.getId(), reviewer.getId());
        return toRequestSummaryDto(request);
    }

    private RequestSummaryDto approveAdjustmentRequest(AdjustmentRequest request, User reviewer) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Request is not pending", HttpStatus.BAD_REQUEST, "REQUEST_NOT_PENDING");
        }
        checkLeaderAuthorization(reviewer, request.getEmployee());

        request.setStatus(RequestStatus.APPROVED);
        request.setReviewedBy(reviewer);
        adjustmentRequestRepository.save(request);

        AttendanceRecord record = request.getAttendanceRecord();
        record.setCheckOutTime(request.getProposedCheckoutTime());
        record.setAttendanceStatus(attendanceService.computeFinalStatus(
            record.getCheckInTime(), request.getProposedCheckoutTime(), record.getShift()));
        record.setApprovalSubStatus(ApprovalSubStatus.APPROVED);
        try {
            attendanceRecordRepository.save(record);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("Record was modified concurrently — please retry", HttpStatus.CONFLICT, "CONCURRENT_UPDATE");
        }

        otCalculationService.recalculate(record);

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
        try {
            attendanceRecordRepository.save(record);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("Record was modified concurrently — please retry", HttpStatus.CONFLICT, "CONCURRENT_UPDATE");
        }

        notificationService.sendRequestRejectedNotification(
            request.getEmployee(), request.getId(),
            "ngoại lệ " + request.getRequestType(), reason);

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
        try {
            attendanceRecordRepository.save(record);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("Record was modified concurrently — please retry", HttpStatus.CONFLICT, "CONCURRENT_UPDATE");
        }

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

    private RequestSummaryDto toRequestSummaryDto(ExceptionRequest request) {
        return RequestSummaryDto.builder()
            .id(request.getId())
            .requestCategory("EXCEPTION")
            .employeeId(request.getEmployee().getId())
            .employeeName(request.getEmployee().getFullName())
            .attendanceRecordId(request.getAttendanceRecord().getId())
            .attendanceDate(request.getAttendanceRecord().getDate())
            .requestType(request.getRequestType())
            .proposedCheckoutTime(null)
            .reason(request.getReason())
            .status(request.getStatus())
            .reviewedBy(request.getReviewedBy() != null ? request.getReviewedBy().getId() : null)
            .reviewReason(request.getReviewReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .build();
    }

    private RequestSummaryDto toRequestSummaryDto(AdjustmentRequest request) {
        return RequestSummaryDto.builder()
            .id(request.getId())
            .requestCategory("ADJUSTMENT")
            .employeeId(request.getEmployee().getId())
            .employeeName(request.getEmployee().getFullName())
            .attendanceRecordId(request.getAttendanceRecord().getId())
            .attendanceDate(request.getAttendanceRecord().getDate())
            .requestType(null)
            .proposedCheckoutTime(request.getProposedCheckoutTime())
            .reason(request.getReason())
            .status(request.getStatus())
            .reviewedBy(request.getReviewedBy() != null ? request.getReviewedBy().getId() : null)
            .reviewReason(request.getReviewReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
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
