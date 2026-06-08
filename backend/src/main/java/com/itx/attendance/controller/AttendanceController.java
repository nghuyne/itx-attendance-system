package com.itx.attendance.controller;

import com.itx.attendance.dto.request.CheckInRequest;
import com.itx.attendance.dto.response.AttendanceRecordDto;
import com.itx.attendance.security.SecurityUtil;
import com.itx.attendance.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        return attendanceService.getTodayRecord()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}/photo/url")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Map<String, String>> getPhotoPresignedUrl(@PathVariable String id) {
        String presignedUrl = attendanceService.getPresignedPhotoUrl(
            id, SecurityUtil.getCurrentUsername(), SecurityUtil.hasRole("ADMIN"));
        return ResponseEntity.ok(Map.of("url", presignedUrl));
    }
}
