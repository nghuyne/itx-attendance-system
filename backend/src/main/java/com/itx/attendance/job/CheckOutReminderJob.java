package com.itx.attendance.job;

import com.itx.attendance.domain.AttendanceRecord;
import com.itx.attendance.domain.AttendanceStatus;
import com.itx.attendance.domain.User;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.HolidayRepository;
import com.itx.attendance.service.EmailService;
import com.itx.attendance.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckOutReminderJob {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final HolidayRepository holidayRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 30 17 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    @Transactional(readOnly = true)
    public void sendCheckOutReminders() {
        LocalDate today = LocalDate.now(TimeUtil.UTC_PLUS_7);

        if (holidayRepository.existsByDate(today)) {
            log.info("CheckOutReminderJob: today is a holiday — skipping");
            return;
        }

        List<AttendanceRecord> candidates = attendanceRecordRepository
            .findByDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNullAndAttendanceStatusNot(
                today, AttendanceStatus.INCOMPLETE);

        int sentCount = 0;
        for (AttendanceRecord record : candidates) {
            User employee = record.getEmployee();
            String employeeFullName = employee.getFullName();
            String shiftEndTime = (record.getShift() != null)
                ? record.getShift().getShiftEndTime().toString()
                : "giờ làm việc";

            String subject = "[ITX Chấm công] Nhắc nhở: Bạn chưa check-out hôm nay";
            String body = String.format(
                "Xin chào %s,%n%nCa làm việc của bạn kết thúc lúc %s. Bạn chưa check-out hôm nay.%n%n" +
                "Vui lòng check-out ngay để hệ thống ghi nhận đầy đủ bản ghi chấm công.%n%nTrân trọng,%nHệ thống ITX Chấm công",
                employeeFullName,
                shiftEndTime
            );
            try {
                emailService.sendEmailAsync(employee, subject, body);
                sentCount++;
            } catch (Exception e) {
                log.warn("CheckOutReminderJob: failed to queue reminder for {} — {}", employee.getId(), e.getMessage());
            }
        }

        log.info("CheckOutReminderJob: sent reminders to {} employees", sentCount);
    }
}
