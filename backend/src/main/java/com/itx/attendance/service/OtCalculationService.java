package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.repository.HolidayRepository;
import com.itx.attendance.repository.OtRecordRepository;
import com.itx.attendance.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtCalculationService {

    private final OtRecordRepository otRecordRepository;
    private final HolidayRepository holidayRepository;

    @Transactional
    public Optional<OtRecord> calculateAndSave(AttendanceRecord record) {
        AttendanceStatus status = record.getAttendanceStatus();
        if (status == AttendanceStatus.INCOMPLETE || status == AttendanceStatus.ABSENT) {
            return Optional.empty();
        }

        if (record.getCheckOutTime() == null) {
            return Optional.empty();
        }

        Shift shift = record.getShift();
        if (shift == null) {
            return Optional.empty();
        }
        LocalTime checkOutVN = TimeUtil.toUtcPlus7(record.getCheckOutTime()).toLocalTime();
        LocalTime otCutoff = shift.getShiftEndTime().plusMinutes(shift.getOtBuffer());

        long otMinutes = ChronoUnit.MINUTES.between(otCutoff, checkOutVN);
        if (otMinutes <= 0) {
            return Optional.empty();
        }

        LocalDate date = record.getDate();
        DayType dayType = determineDayType(date);
        BigDecimal multiplier = getMultiplier(dayType);

        OtRecord otRecord = OtRecord.builder()
            .employee(record.getEmployee())
            .attendanceRecord(record)
            .date(date)
            .otDurationMinutes((int) otMinutes)
            .dayType(dayType)
            .otMultiplier(multiplier)
            .build();

        OtRecord saved = otRecordRepository.save(otRecord);
        log.info("OT record created: employee={}, date={}, minutes={}, dayType={}, multiplier={}",
            record.getEmployee().getId(), date, otMinutes, dayType, multiplier);
        return Optional.of(saved);
    }

    @Transactional
    public Optional<OtRecord> recalculate(AttendanceRecord record) {
        otRecordRepository.deleteByAttendanceRecordId(record.getId());
        return calculateAndSave(record);
    }

    private DayType determineDayType(LocalDate date) {
        if (holidayRepository.existsByDate(date)) {
            return DayType.HOLIDAY;
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return DayType.WEEKEND;
        }
        return DayType.WEEKDAY;
    }

    private BigDecimal getMultiplier(DayType dayType) {
        return switch (dayType) {
            case HOLIDAY -> new BigDecimal("3.0");
            case WEEKEND -> new BigDecimal("2.0");
            case WEEKDAY -> new BigDecimal("1.5");
        };
    }
}
