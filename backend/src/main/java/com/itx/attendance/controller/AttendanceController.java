package com.itx.attendance.controller;

import com.itx.attendance.dto.request.CheckInRequest;
import com.itx.attendance.dto.request.CheckOutRequest;
import com.itx.attendance.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
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

    @PostMapping("/check-out")
    public ResponseEntity<AttendanceRecordDto> checkOut(
            @Valid @RequestBody CheckOutRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(attendanceService.checkOut(request, httpRequest));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<AttendanceRecordDto>> getHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(
                "Tham số phân trang không hợp lệ", HttpStatus.BAD_REQUEST, "INVALID_PAGINATION");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(
                "Invalid date range", HttpStatus.BAD_REQUEST, "INVALID_DATE");
        }
        return ResponseEntity.ok(attendanceService.getHistory(
            from, to, PageRequest.of(page, size, Sort.by("date").descending())));
    }

    @GetMapping("/{id}/photo/url")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    public ResponseEntity<Map<String, String>> getPhotoPresignedUrl(@PathVariable String id) {
        String presignedUrl = attendanceService.getPresignedPhotoUrl(
            id, SecurityUtil.getCurrentUsername(), SecurityUtil.hasRole("ADMIN"));
        return ResponseEntity.ok(Map.of("url", presignedUrl));
    }
}
