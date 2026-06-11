package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.dto.request.AttendanceOverrideRequest;
import com.itx.attendance.dto.response.AdminAttendanceRecordDto;
import com.itx.attendance.dto.response.AttendanceRecordDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminOverrideService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AuditLogRepository auditLogRepository;
    private final AttendanceService attendanceService;
    private final OtCalculationService otCalculationService;

    @Transactional
    public AttendanceRecordDto overrideAttendance(String recordId, AttendanceOverrideRequest req, User admin) {
        AttendanceRecord record = attendanceRecordRepository.findById(recordId)
            .orElseThrow(() -> new BusinessException(
                "Không tìm thấy bản ghi", HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND"));

        boolean timeChanged = false;

        if (req.checkInTime() != null) {
            String oldVal = formatForAudit(record.getCheckInTime());
            record.setCheckInTime(req.checkInTime());
            auditLogRepository.save(new AuditLog(admin, "attendance_records", recordId,
                "check_in_time", oldVal, formatForAudit(req.checkInTime()), req.auditReason()));
            timeChanged = true;
        }

        if (req.checkOutTime() != null) {
            String oldVal = formatForAudit(record.getCheckOutTime());
            record.setCheckOutTime(req.checkOutTime());
            auditLogRepository.save(new AuditLog(admin, "attendance_records", recordId,
                "check_out_time", oldVal, formatForAudit(req.checkOutTime()), req.auditReason()));
            timeChanged = true;
        }

        if (req.attendanceStatus() != null && !timeChanged) {
            String oldVal = record.getAttendanceStatus().name();
            record.setAttendanceStatus(req.attendanceStatus());
            auditLogRepository.save(new AuditLog(admin, "attendance_records", recordId,
                "attendance_status", oldVal, req.attendanceStatus().name(), req.auditReason()));
        }

        if (req.photoUrl() != null) {
            String oldVal = record.getCheckInPhotoUrl();
            record.setCheckInPhotoUrl(req.photoUrl());
            auditLogRepository.save(new AuditLog(admin, "attendance_records", recordId,
                "check_in_photo_url", oldVal, req.photoUrl(), req.auditReason()));
        }

        record.setAdminOverride(true);
        record.setApprovalSubStatus(ApprovalSubStatus.ADMIN_OVERRIDE);

        if (timeChanged) {
            AttendanceStatus recalculated = attendanceService.computeFinalStatus(
                record.getCheckInTime(), record.getCheckOutTime(), record.getShift());
            String oldStatusVal = record.getAttendanceStatus().name();
            record.setAttendanceStatus(recalculated);
            if (!oldStatusVal.equals(recalculated.name())) {
                auditLogRepository.save(new AuditLog(admin, "attendance_records", recordId,
                    "attendance_status", oldStatusVal, recalculated.name(), req.auditReason()));
            }
            otCalculationService.recalculate(record);
        }

        attendanceRecordRepository.save(record);
        return attendanceService.toDto(record);
    }

    public Page<AdminAttendanceRecordDto> searchAttendance(
            LocalDate from, LocalDate to, String employeeId, Pageable pageable) {
        Page<AttendanceRecord> records;
        if (employeeId != null && !employeeId.isBlank()) {
            records = attendanceRecordRepository.findByEmployeeIdAndDateBetween(employeeId, from, to, pageable);
        } else {
            records = attendanceRecordRepository.findByDateBetween(from, to, pageable);
        }
        return records.map(this::toAdminDto);
    }

    private AdminAttendanceRecordDto toAdminDto(AttendanceRecord r) {
        Shift shift = r.getShift();
        return AdminAttendanceRecordDto.builder()
            .id(r.getId())
            .employeeId(r.getEmployee().getId())
            .employeeName(r.getEmployee().getFullName())
            .shiftId(shift != null ? shift.getId() : null)
            .shiftName(shift != null ? shift.getName() : null)
            .date(r.getDate())
            .checkInTime(r.getCheckInTime())
            .checkOutTime(r.getCheckOutTime())
            .checkInPhotoUrl(r.getCheckInPhotoUrl())
            .checkOutPhotoUrl(r.getCheckOutPhotoUrl())
            .attendanceStatus(r.getAttendanceStatus())
            .approvalSubStatus(r.getApprovalSubStatus())
            .isAdminOverride(r.isAdminOverride())
            .version(r.getVersion())
            .createdAt(r.getCreatedAt())
            .build();
    }

    private String formatForAudit(LocalDateTime dt) {
        return dt != null ? dt.toString() : null;
    }
}
