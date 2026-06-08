package com.itx.attendance.controller;

import com.itx.attendance.domain.AttendanceRecord;
import com.itx.attendance.domain.User;
import com.itx.attendance.dto.request.CheckInRequest;
import com.itx.attendance.dto.response.AttendanceRecordDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.security.SecurityUtil;
import com.itx.attendance.service.AttendanceService;
import com.itx.attendance.service.PhotoService;
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
    private final PhotoService photoService;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserRepository userRepository;

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
        String username = SecurityUtil.getCurrentUsername();
        AttendanceRecord record = attendanceRecordRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                "Record not found", HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND"));

        if (!SecurityUtil.hasRole("ADMIN")) {
            User employee = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(
                    "User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
            if (!record.getEmployee().getId().equals(employee.getId())) {
                throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN, "FORBIDDEN");
            }
        }

        String objectKey = extractObjectKey(record.getCheckInPhotoUrl());
        if (objectKey == null) {
            throw new BusinessException(
                "No photo available", HttpStatus.NOT_FOUND, "NO_PHOTO");
        }

        String presignedUrl = photoService.getPresignedUrl(objectKey);
        return ResponseEntity.ok(Map.of("url", presignedUrl));
    }

    private String extractObjectKey(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) return null;
        int idx = photoUrl.indexOf("/attendance/");
        if (idx == -1) return null;
        return photoUrl.substring(idx + "/attendance/".length());
    }
}
