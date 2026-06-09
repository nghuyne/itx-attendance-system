package com.itx.attendance.controller;

import com.itx.attendance.dto.request.AdjustmentRequestCreateDto;
import com.itx.attendance.dto.request.ExceptionRequestCreateDto;
import com.itx.attendance.dto.response.AdjustmentRequestDto;
import com.itx.attendance.dto.response.ExceptionRequestDto;
import com.itx.attendance.service.RequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("hasRole('EMPLOYEE')")
public class RequestController {

    private final RequestService requestService;

    @PostMapping("/exception")
    public ResponseEntity<ExceptionRequestDto> submitExceptionRequest(
            @Valid @RequestBody ExceptionRequestCreateDto request) {
        log.info("Exception request submission: recordId={}, type={}",
            request.attendanceRecordId(), request.requestType());
        ExceptionRequestDto result = requestService.submitExceptionRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/adjustment")
    public ResponseEntity<AdjustmentRequestDto> submitAdjustmentRequest(
            @Valid @RequestBody AdjustmentRequestCreateDto request) {
        log.info("Adjustment request submission: recordId={}, proposedCheckoutTime={}",
            request.attendanceRecordId(), request.proposedCheckoutTime());
        AdjustmentRequestDto result = requestService.submitAdjustmentRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
