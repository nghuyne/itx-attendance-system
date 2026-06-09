package com.itx.attendance.job;

import com.itx.attendance.domain.AttendanceRecord;
import com.itx.attendance.domain.AttendanceStatus;
import com.itx.attendance.domain.Notification;
import com.itx.attendance.domain.NotificationType;
import com.itx.attendance.domain.Shift;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.NotificationRepository;
import com.itx.attendance.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncompleteAttendanceJob {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 */5 7-22 * * ?", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void markIncompleteRecords() {
        LocalDateTime nowVn = TimeUtil.nowUtcPlus7();
        LocalDate today = nowVn.toLocalDate();
        LocalTime nowTime = nowVn.toLocalTime();

        List<AttendanceRecord> candidates = attendanceRecordRepository
            .findByCheckOutTimeIsNullAndAttendanceStatusNotIn(
                List.of(AttendanceStatus.INCOMPLETE, AttendanceStatus.ABSENT));

        int markedCount = 0;
        for (AttendanceRecord record : candidates) {
            if (shouldMarkIncomplete(record, today, nowTime)) {
                record.setAttendanceStatus(AttendanceStatus.INCOMPLETE);
                attendanceRecordRepository.save(record);

                notificationRepository.save(Notification.builder()
                    .recipient(record.getEmployee())
                    .type(NotificationType.INCOMPLETE_RECORD)
                    .referenceId(record.getId())
                    .message("Bản ghi ngày " + record.getDate() + " chưa hoàn chỉnh")
                    .build());

                markedCount++;
            }
        }

        if (markedCount > 0) {
            log.info("IncompleteAttendanceJob: marked {} records as INCOMPLETE", markedCount);
        }
    }

    private boolean shouldMarkIncomplete(AttendanceRecord record, LocalDate today, LocalTime nowTime) {
        if (record.getDate().isBefore(today)) {
            return true;
        }
        Shift shift = record.getShift();
        LocalTime graceCutoff = shift.getShiftEndTime()
            .plusMinutes(shift.getOtBuffer())
            .plusMinutes(30);
        return nowTime.isAfter(graceCutoff);
    }
}
