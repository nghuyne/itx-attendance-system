package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.dto.request.CheckInRequest;
import com.itx.attendance.dto.response.AttendanceRecordDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.repository.ValidIpRepository;
import com.itx.attendance.security.SecurityUtil;
import com.itx.attendance.util.TimeUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserRepository userRepository;
    private final ValidIpRepository validIpRepository;

    @Transactional
    public AttendanceRecordDto checkIn(CheckInRequest request, HttpServletRequest httpRequest) {
        String username = SecurityUtil.getCurrentUsername();
        User employee = userRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException(
                "User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        Shift shift = employee.getShift();
        if (shift == null) {
            throw new BusinessException(
                "Nhân viên chưa được gán ca làm việc",
                HttpStatus.BAD_REQUEST, "NO_SHIFT_ASSIGNED");
        }

        LocalDate today = LocalDate.now(TimeUtil.UTC_PLUS_7);

        if (attendanceRecordRepository.existsByEmployeeIdAndDate(employee.getId(), today)) {
            throw new BusinessException(
                "Đã chấm công rồi", HttpStatus.CONFLICT, "ALREADY_CHECKED_IN");
        }

        String clientIp = extractClientIp(httpRequest);

        if (!request.isClientSite()) {
            boolean companyValid = validIpRepository
                .existsByIpAddressAndScopeAndEmployeeIsNullAndActiveTrue(clientIp, IpScope.COMPANY);
            boolean individualValid = validIpRepository
                .existsByIpAddressAndScopeAndEmployeeIdAndActiveTrue(clientIp, IpScope.INDIVIDUAL, employee.getId());

            if (!companyValid && !individualValid) {
                throw new BusinessException(
                    "Không nhận diện được mạng văn phòng",
                    HttpStatus.FORBIDDEN, "INVALID_IP");
            }
        }

        LocalDateTime checkInUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalTime checkInVN = TimeUtil.toUtcPlus7(checkInUtc).toLocalTime();
        AttendanceStatus initialStatus = calculateInitialStatus(checkInVN, shift);

        AttendanceRecord record = AttendanceRecord.builder()
            .employee(employee)
            .shift(shift)
            .date(today)
            .checkInTime(checkInUtc)
            .checkInIp(clientIp)
            .checkInLat(request.lat() != null ? BigDecimal.valueOf(request.lat()) : null)
            .checkInLng(request.lng() != null ? BigDecimal.valueOf(request.lng()) : null)
            .attendanceStatus(initialStatus)
            .clientSite(request.isClientSite())
            .gpsUnavailable(request.lat() == null)
            .build();

        try {
            return toDto(attendanceRecordRepository.save(record));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                "Đã chấm công rồi", HttpStatus.CONFLICT, "ALREADY_CHECKED_IN");
        }
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceRecordDto> getTodayRecord() {
        String username = SecurityUtil.getCurrentUsername();
        User employee = userRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException(
                "User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        LocalDate today = LocalDate.now(TimeUtil.UTC_PLUS_7);
        return attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), today)
            .map(this::toDto);
    }

    private AttendanceStatus calculateInitialStatus(LocalTime checkInVN, Shift shift) {
        long minutesLate = ChronoUnit.MINUTES.between(shift.getShiftStartTime(), checkInVN);
        if (minutesLate > shift.getHalfDayThreshold()) {
            return AttendanceStatus.HALF_DAY;
        }
        if (minutesLate > shift.getLateInThreshold()) {
            return AttendanceStatus.LATE_IN;
        }
        return AttendanceStatus.ON_TIME;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded)) {
            String ip = forwarded.split(",")[0].strip();
            return stripIpv6Prefix(ip);
        }
        return stripIpv6Prefix(request.getRemoteAddr());
    }

    private String stripIpv6Prefix(String ip) {
        if (ip != null && ip.startsWith("::ffff:")) {
            return ip.substring(7);
        }
        return ip;
    }

    AttendanceRecordDto toDto(AttendanceRecord record) {
        Shift shift = record.getShift();
        return AttendanceRecordDto.builder()
            .id(record.getId())
            .employeeId(record.getEmployee().getId())
            .shiftId(shift.getId())
            .shiftName(shift.getName())
            .shiftStartTime(shift.getShiftStartTime().format(TIME_FORMATTER))
            .shiftEndTime(shift.getShiftEndTime().format(TIME_FORMATTER))
            .date(record.getDate())
            .checkInTime(record.getCheckInTime())
            .checkInIp(record.getCheckInIp())
            .checkInLat(record.getCheckInLat())
            .checkInLng(record.getCheckInLng())
            .checkInPhotoUrl(record.getCheckInPhotoUrl())
            .checkOutTime(record.getCheckOutTime())
            .checkOutIp(record.getCheckOutIp())
            .checkOutLat(record.getCheckOutLat())
            .checkOutLng(record.getCheckOutLng())
            .checkOutPhotoUrl(record.getCheckOutPhotoUrl())
            .attendanceStatus(record.getAttendanceStatus())
            .approvalSubStatus(record.getApprovalSubStatus())
            .isClientSite(record.isClientSite())
            .gpsUnavailable(record.isGpsUnavailable())
            .suspiciousLocation(record.isSuspiciousLocation())
            .isAdminOverride(record.isAdminOverride())
            .version(record.getVersion())
            .createdAt(record.getCreatedAt())
            .build();
    }
}
