package com.itx.attendance.job;

import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.HolidayRepository;
import com.itx.attendance.repository.LeaveRequestRepository;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.service.EmailService;
import com.itx.attendance.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInReminderJob {

    private final UserRepository userRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 15 8 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    @Transactional(readOnly = true)
    public void sendCheckInReminders() {
        LocalDate today = LocalDate.now(TimeUtil.UTC_PLUS_7);

        if (holidayRepository.existsByDate(today)) {
            log.info("CheckInReminderJob: today is a holiday — skipping");
            return;
        }

        List<User> employees = userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE);
        Set<String> onLeave = leaveRequestRepository.findApprovedEmployeeIdsForDate(today);

        int sentCount = 0;
        for (User employee : employees) {
            if (onLeave.contains(employee.getId())) continue;
            if (attendanceRecordRepository.existsByEmployeeIdAndDateAndCheckInTimeIsNotNull(employee.getId(), today)) continue;

            String subject = "[ITX Chấm công] Nhắc nhở: Bạn chưa check-in hôm nay";
            String body = String.format(
                "Xin chào %s,%n%nCa làm việc của bạn bắt đầu lúc %s. Bạn chưa check-in hôm nay.%n%n" +
                "Vui lòng check-in ngay để tránh bị đánh dấu vắng mặt.%n%nTrân trọng,%nHệ thống ITX Chấm công",
                employee.getFullName(),
                employee.getShift().getShiftStartTime().toString()
            );
            try {
                emailService.sendEmailAsync(employee, subject, body);
                sentCount++;
            } catch (Exception e) {
                log.warn("CheckInReminderJob: failed to queue reminder for {} — {}", employee.getId(), e.getMessage());
            }
        }

        log.info("CheckInReminderJob: sent reminders to {} employees", sentCount);
    }
}
