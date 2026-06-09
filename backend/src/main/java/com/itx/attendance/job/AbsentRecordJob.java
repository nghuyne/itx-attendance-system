package com.itx.attendance.job;

import com.itx.attendance.domain.AttendanceRecord;
import com.itx.attendance.domain.AttendanceStatus;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AbsentRecordJob {

    private final UserRepository userRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void createAbsentRecords() {
        LocalDate yesterday = LocalDate.now(TimeUtil.UTC_PLUS_7).minusDays(1);

        List<User> activeEmployees = userRepository
            .findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE);

        int createdCount = 0;
        for (User employee : activeEmployees) {
            if (!attendanceRecordRepository.existsByEmployeeIdAndDate(employee.getId(), yesterday)) {
                try {
                    attendanceRecordRepository.save(AttendanceRecord.builder()
                        .employee(employee)
                        .shift(employee.getShift())
                        .date(yesterday)
                        .attendanceStatus(AttendanceStatus.ABSENT)
                        .build());
                    createdCount++;
                } catch (DataIntegrityViolationException e) {
                    String message = e.getCause() != null ? e.getCause().getMessage() : "";
                    if (message.contains("Duplicate entry")) {
                        log.debug("AbsentRecordJob: duplicate record skipped for employee {} on {}",
                            employee.getId(), yesterday);
                    } else if (message.contains("foreign key")) {
                        log.warn("AbsentRecordJob: FK constraint violation for employee {} on {}: {}",
                            employee.getId(), yesterday, message);
                    } else {
                        throw e;
                    }
                }
            }
        }

        log.info("AbsentRecordJob: created {} ABSENT records for {}", createdCount, yesterday);
    }
}
