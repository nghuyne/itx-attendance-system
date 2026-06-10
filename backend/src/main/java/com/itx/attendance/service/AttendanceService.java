package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.dto.request.CheckInRequest;
import com.itx.attendance.dto.request.CheckOutRequest;
import com.itx.attendance.dto.response.AttendanceRecordDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.repository.ValidIpRepository;
import com.itx.attendance.security.SecurityUtil;
import com.itx.attendance.util.HaversineUtil;
import com.itx.attendance.util.TimeUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserRepository userRepository;
    private final ValidIpRepository validIpRepository;
    private final PhotoService photoService;
    private final OtCalculationService otCalculationService;

    // Self-reference via proxy so @Transactional on persistCheckIn / getPresignedPhotoUrl is honoured.
    // @Lazy breaks the circular dependency during bean construction.
    @Autowired @Lazy
    private AttendanceService self;

    // Not @Transactional: releases DB connection before MinIO I/O so the connection pool
    // is not held during network upload latency.
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

        if (request.isClientSite() && (request.lat() == null || request.lng() == null)) {
            throw new BusinessException(
                "Yêu cầu GPS để chấm công ngoài văn phòng",
                HttpStatus.BAD_REQUEST, "GPS_REQUIRED");
        }

        // Decode and validate photo synchronously before the async upload
        PhotoService.PhotoData photoData = photoService.decodeBase64Photo(request.photoBase64());
        String objectKey = employee.getId() + "/" + today + "/checkin_" + UUID.randomUUID() + ".jpg";
        String checkInObjectKey;
        try {
            checkInObjectKey = photoService.uploadPhotoAsync(photoData.bytes(), objectKey, photoData.contentType()).get();
        } catch (ExecutionException e) {
            throw new BusinessException(
                "Lỗi upload ảnh", HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_UPLOAD_FAILED");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                "Lỗi upload ảnh", HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_UPLOAD_FAILED");
        }

        boolean suspiciousLocation = false;
        if (request.lat() != null && request.lng() != null) {
            LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(24);
            Optional<AttendanceRecord> prevOpt = attendanceRecordRepository
                .findFirstByEmployeeIdAndCheckInTimeAfterOrderByCheckInTimeDesc(employee.getId(), since);
            if (prevOpt.isPresent()) {
                AttendanceRecord prev = prevOpt.get();
                if (!prev.isGpsUnavailable()
                        && prev.getCheckInLat() != null
                        && prev.getCheckInLng() != null
                        && prev.getCheckInTime() != null) {
                    double distKm = HaversineUtil.distanceKm(
                        prev.getCheckInLat().doubleValue(), prev.getCheckInLng().doubleValue(),
                        request.lat(), request.lng());
                    long minutesDelta = ChronoUnit.MINUTES.between(
                        prev.getCheckInTime(), LocalDateTime.now(ZoneOffset.UTC));
                    if (distKm > 50.0 && minutesDelta < 60) {
                        suspiciousLocation = true;
                    }
                }
            }
        }

        return self.persistCheckIn(employee, shift, today, clientIp, request, checkInObjectKey, suspiciousLocation);
    }

    @Transactional
    public AttendanceRecordDto persistCheckIn(User employee, Shift shift, LocalDate today,
            String clientIp, CheckInRequest request, String checkInObjectKey, boolean suspiciousLocation) {
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
            .checkInPhotoUrl(checkInObjectKey)
            .attendanceStatus(initialStatus)
            .clientSite(request.isClientSite())
            .gpsUnavailable(request.lat() == null || request.lng() == null)
            .suspiciousLocation(suspiciousLocation)
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

    @Transactional(readOnly = true)
    public String getPresignedPhotoUrl(String recordId, String username, boolean isAdmin) {
        AttendanceRecord record = attendanceRecordRepository.findById(recordId)
            .orElseThrow(() -> new BusinessException(
                "Record not found", HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND"));

        if (!isAdmin) {
            User employee = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(
                    "User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
            if (!record.getEmployee().getId().equals(employee.getId())) {
                throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN, "FORBIDDEN");
            }
        }

        String objectKey = record.getCheckInPhotoUrl();
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException("No photo available", HttpStatus.NOT_FOUND, "NO_PHOTO");
        }

        return photoService.getPresignedUrl(objectKey);
    }

    public AttendanceRecordDto checkOut(CheckOutRequest request, HttpServletRequest httpRequest) {
        String username = SecurityUtil.getCurrentUsername();
        User employee = userRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException(
                "User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        LocalDate today = LocalDate.now(TimeUtil.UTC_PLUS_7);

        AttendanceRecord existing = attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), today)
            .orElseThrow(() -> new BusinessException(
                "Chưa có bản ghi check-in hôm nay", HttpStatus.BAD_REQUEST, "NO_CHECKIN_FOUND"));

        if (existing.getCheckOutTime() != null) {
            throw new BusinessException(
                "Đã check-out rồi", HttpStatus.CONFLICT, "ALREADY_CHECKED_OUT");
        }

        PhotoService.PhotoData photoData = photoService.decodeBase64Photo(request.photoBase64());
        String objectKey = employee.getId() + "/" + today + "/checkout_" + UUID.randomUUID() + ".jpg";
        String checkOutObjectKey;
        try {
            checkOutObjectKey = photoService.uploadPhotoAsync(photoData.bytes(), objectKey, photoData.contentType()).get();
        } catch (ExecutionException e) {
            throw new BusinessException("Lỗi upload ảnh", HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_UPLOAD_FAILED");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Lỗi upload ảnh", HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_UPLOAD_FAILED");
        }

        String checkOutIp = extractClientIp(httpRequest);
        return self.persistCheckOut(employee, today, request, checkOutObjectKey, checkOutIp);
    }

    @Transactional
    public AttendanceRecordDto persistCheckOut(User employee, LocalDate today,
            CheckOutRequest request, String checkOutObjectKey, String checkOutIp) {
        AttendanceRecord record = attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), today)
            .orElseThrow(() -> new BusinessException(
                "Chưa có bản ghi check-in hôm nay", HttpStatus.BAD_REQUEST, "NO_CHECKIN_FOUND"));

        if (record.getCheckOutTime() != null) {
            throw new BusinessException(
                "Đã check-out rồi", HttpStatus.CONFLICT, "ALREADY_CHECKED_OUT");
        }

        LocalDateTime checkOutUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalTime checkOutVN = TimeUtil.toUtcPlus7(checkOutUtc).toLocalTime();
        LocalTime checkInVN = TimeUtil.toUtcPlus7(record.getCheckInTime()).toLocalTime();
        Shift shift = record.getShift();
        if (shift == null) {
            throw new BusinessException(
                "Không tìm thấy ca làm việc", HttpStatus.BAD_REQUEST, "NO_SHIFT_ASSIGNED");
        }

        AttendanceStatus finalStatus = calculateFinalStatus(checkInVN, checkOutVN, shift);

        record.setCheckOutTime(checkOutUtc);
        record.setCheckOutIp(checkOutIp);
        record.setCheckOutLat(request.lat() != null ? BigDecimal.valueOf(request.lat()) : null);
        record.setCheckOutLng(request.lng() != null ? BigDecimal.valueOf(request.lng()) : null);
        record.setCheckOutPhotoUrl(checkOutObjectKey);
        record.setAttendanceStatus(finalStatus);

        AttendanceRecord saved;
        try {
            saved = attendanceRecordRepository.save(record);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(
                "Đã check-out rồi", HttpStatus.CONFLICT, "ALREADY_CHECKED_OUT");
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                "Lỗi lưu dữ liệu check-out", HttpStatus.CONFLICT, "CHECKOUT_SAVE_FAILED");
        }
        otCalculationService.calculateAndSave(saved);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<AttendanceRecordDto> getHistory(LocalDate from, LocalDate to, Pageable pageable) {
        String username = SecurityUtil.getCurrentUsername();
        User employee = userRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException(
                "User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        return attendanceRecordRepository.findByEmployeeIdAndDateBetween(employee.getId(), from, to, pageable)
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

    public AttendanceStatus computeFinalStatus(LocalDateTime checkInUtc, LocalDateTime checkOutUtc, Shift shift) {
        LocalTime checkInVN = TimeUtil.toUtcPlus7(checkInUtc).toLocalTime();
        LocalTime checkOutVN = TimeUtil.toUtcPlus7(checkOutUtc).toLocalTime();
        return calculateFinalStatus(checkInVN, checkOutVN, shift);
    }

    private AttendanceStatus calculateFinalStatus(LocalTime checkInVN, LocalTime checkOutVN, Shift shift) {
        long minutesLate = ChronoUnit.MINUTES.between(shift.getShiftStartTime(), checkInVN);
        long minutesEarlyOut = ChronoUnit.MINUTES.between(checkOutVN, shift.getShiftEndTime());

        boolean halfDayViaLate = minutesLate > shift.getHalfDayThreshold();
        boolean halfDayViaEarly = minutesEarlyOut > shift.getHalfDayThreshold();
        boolean lateIn = minutesLate > shift.getLateInThreshold();
        boolean earlyOut = minutesEarlyOut > shift.getEarlyOutThreshold();

        if (halfDayViaLate || halfDayViaEarly) return AttendanceStatus.HALF_DAY;
        if (lateIn && earlyOut) return AttendanceStatus.LATE_IN_EARLY_OUT;
        if (lateIn) return AttendanceStatus.LATE_IN;
        if (earlyOut) return AttendanceStatus.EARLY_OUT;
        return AttendanceStatus.ON_TIME;
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
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
