package com.itx.attendance.job;

import com.itx.attendance.domain.ApprovalSubStatus;
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

        LocalDateTime lookbackFrom = today.minusDays(7).atStartOfDay();
        List<AttendanceRecord> candidates = attendanceRecordRepository
            .findByCheckInTimeAfterAndCheckOutTimeIsNullAndAttendanceStatusNotIn(
                lookbackFrom,
                List.of(AttendanceStatus.INCOMPLETE, AttendanceStatus.ABSENT, AttendanceStatus.EXCUSED));

        int markedCount = 0;
        for (AttendanceRecord record : candidates) {
            ApprovalSubStatus subStatus = record.getApprovalSubStatus();
            if (subStatus == ApprovalSubStatus.PENDING_ADJUSTMENT
                    || subStatus == ApprovalSubStatus.PENDING_APPROVAL) {
                continue;
            }
            if (shouldMarkIncomplete(record, nowVn)) {
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

    private boolean shouldMarkIncomplete(AttendanceRecord record, LocalDateTime nowVn) {
        Shift shift = record.getShift();
        if (shift == null) {
            // No shift to anchor a grace cutoff to — fall back to the plain calendar-day check.
            if (record.getDate().isBefore(nowVn.toLocalDate())) {
                return true;
            }
            log.warn("IncompleteAttendanceJob: skipping record {} — shift is null", record.getId());
            return false;
        }
        // Anchor the cutoff to the record's own date via LocalDateTime (not LocalTime), so a
        // late shift end + otBuffer + 30 that rolls past midnight advances to the next calendar
        // day instead of wrapping — and doesn't get short-circuited by a plain date comparison
        // the moment the clock ticks past midnight.
        LocalDateTime graceCutoff = LocalDateTime.of(record.getDate(), shift.getShiftEndTime())
            .plusMinutes(shift.getOtBuffer())
            .plusMinutes(30);
        return nowVn.isAfter(graceCutoff);
    }
}
