package com.itx.attendance.controller;

import com.itx.attendance.dto.request.CheckInRequest;
import com.itx.attendance.dto.response.AttendanceRecordDto;
import com.itx.attendance.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('EMPLOYEE')")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    public ResponseEntity<AttendanceRecordDto> checkIn(
            @Valid @RequestBody CheckInRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(attendanceService.checkIn(request, httpRequest));
    }

    @GetMapping("/today")
    public ResponseEntity<AttendanceRecordDto> getToday() {
        return ResponseEntity.ok(attendanceService.getTodayRecord().orElse(null));
    }
}
