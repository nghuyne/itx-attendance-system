package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.dto.request.AttendanceOverrideRequest;
import com.itx.attendance.dto.response.AdminAttendanceRecordDto;
import com.itx.attendance.dto.response.AttendanceRecordDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
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
        // Reject ambiguous combination: explicit status + time change means intent is unclear
        boolean timePending = req.checkInTime() != null || req.checkOutTime() != null;
        if (req.attendanceStatus() != null && timePending) {
            throw new BusinessException(
                "Không thể đặt trạng thái thủ công khi đồng thời thay đổi thời gian. Hãy chỉ gửi một trong hai.",
                HttpStatus.BAD_REQUEST, "AMBIGUOUS_OVERRIDE");
        }

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

        // Validate temporal order after applying time changes
        if (record.getCheckInTime() != null && record.getCheckOutTime() != null
                && !record.getCheckInTime().isBefore(record.getCheckOutTime())) {
            throw new BusinessException(
                "Giờ ra phải sau giờ vào", HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE");
        }

        if (req.attendanceStatus() != null) {
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

        // Only recalculate status when both times are present; a partial-time override
        // (e.g. ABSENT record with only checkInTime set) cannot produce a meaningful final status
        if (timeChanged && record.getCheckOutTime() != null) {
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

        try {
            attendanceRecordRepository.save(record);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(
                "Bản ghi đã được chỉnh sửa bởi người khác, vui lòng tải lại trang",
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        }
        return attendanceService.toDto(record);
    }

    public Page<AdminAttendanceRecordDto> searchAttendance(
            LocalDate from, LocalDate to, String employeeId, Pageable pageable) {
        if (from.isAfter(to)) {
            throw new BusinessException(
                "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc", HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
        }
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
