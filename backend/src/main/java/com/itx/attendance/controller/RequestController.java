package com.itx.attendance.controller;

import com.itx.attendance.domain.RequestStatus;
import com.itx.attendance.domain.User;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.dto.request.AdjustmentRequestCreateDto;
import com.itx.attendance.dto.request.ExceptionRequestCreateDto;
import com.itx.attendance.dto.request.RequestRejectDto;
import com.itx.attendance.dto.response.AdjustmentRequestDto;
import com.itx.attendance.dto.response.ExceptionRequestDto;
import com.itx.attendance.dto.response.RequestSummaryDto;
import com.itx.attendance.service.CurrentUserService;
import com.itx.attendance.service.RequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
@Validated
@Slf4j
public class RequestController {

    private final RequestService requestService;
    private final CurrentUserService currentUserService;

    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/exception")
    public ResponseEntity<ExceptionRequestDto> submitExceptionRequest(
            @Valid @RequestBody ExceptionRequestCreateDto request) {
        log.info("Exception request submission: recordId={}, type={}",
            request.attendanceRecordId(), request.requestType());
        ExceptionRequestDto result = requestService.submitExceptionRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/adjustment")
    public ResponseEntity<AdjustmentRequestDto> submitAdjustmentRequest(
            @Valid @RequestBody AdjustmentRequestCreateDto request) {
        log.info("Adjustment request submission: recordId={}, proposedCheckoutTime={}",
            request.attendanceRecordId(), request.proposedCheckoutTime());
        AdjustmentRequestDto result = requestService.submitAdjustmentRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PreAuthorize("hasAnyRole('LEADER', 'ADMIN')")
    @GetMapping("/pending")
    public ResponseEntity<List<RequestSummaryDto>> getPendingRequests() {
        User reviewer = currentUserService.getCurrentUser();
        return ResponseEntity.ok(requestService.getPendingRequests(reviewer));
    }

    @PreAuthorize("hasAnyRole('LEADER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<RequestSummaryDto>> getRequestsByStatus(
            @RequestParam RequestStatus status) {
        if (status == RequestStatus.PENDING) {
            throw new BusinessException("Use /pending endpoint for pending requests", HttpStatus.BAD_REQUEST, "INVALID_STATUS_PARAM");
        }
        User currentUser = currentUserService.getCurrentUser();
        return ResponseEntity.ok(requestService.getRequestsByStatus(currentUser, status));
    }

    @PreAuthorize("hasAnyRole('LEADER', 'ADMIN')")
    @PutMapping("/{id}/approve")
    public ResponseEntity<RequestSummaryDto> approveRequest(@PathVariable String id) {
        User reviewer = currentUserService.getCurrentUser();
        return ResponseEntity.ok(requestService.approveRequest(id, reviewer));
    }

    @PreAuthorize("hasAnyRole('LEADER', 'ADMIN')")
    @PutMapping("/{id}/reject")
    public ResponseEntity<RequestSummaryDto> rejectRequest(
            @PathVariable String id,
            @Valid @RequestBody RequestRejectDto body) {
        User reviewer = currentUserService.getCurrentUser();
        return ResponseEntity.ok(requestService.rejectRequest(id, body.reason(), reviewer));
    }

}
