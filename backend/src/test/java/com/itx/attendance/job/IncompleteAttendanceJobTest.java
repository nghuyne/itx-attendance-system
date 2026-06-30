package com.itx.attendance.job;

import com.itx.attendance.domain.*;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.NotificationRepository;
import com.itx.attendance.util.TimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Story 3.5 — IncompleteAttendanceJob (FR-9).
 *
 * Grace period cutoff = shift_end + ot_buffer + 30 min.
 * Shift used in tests: 08:00–17:00, ot_buffer=30 → cutoff = 18:00 VN.
 */
@ExtendWith(MockitoExtension.class)
class IncompleteAttendanceJobTest {

    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks
    private IncompleteAttendanceJob incompleteAttendanceJob;

    private User employee;
    private Shift shift;

    @BeforeEach
    void setUp() {
        employee = User.builder()
            .id("emp-1")
            .username("emp1")
            .fullName("Nguyen Van A")
            .role(UserRole.EMPLOYEE)
            .build();

        // Shift 08:00–17:00, ot_buffer=30 → grace cutoff = 17:00 + 30 + 30 = 18:00 VN
        shift = Shift.builder()
            .id("shift-1")
            .name("Ca Sáng")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .otBuffer(30)
            .build();
    }

    private AttendanceRecord makeRecord(LocalDate date, ApprovalSubStatus subStatus) {
        return AttendanceRecord.builder()
            .id("rec-1")
            .employee(employee)
            .shift(shift)
            .date(date)
            .checkInTime(date.atTime(1, 0))
            .checkInIp("127.0.0.1")
            .checkInPhotoUrl("photo.jpg")
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .approvalSubStatus(subStatus)
            .build();
    }

    // ── Past-day records ───────────────────────────────────────────────────

    @Test
    void markIncompleteRecords_pastDayRecord_isMarkedIncomplete() {
        // Records from before today are always marked INCOMPLETE regardless of time
        LocalDate yesterday = LocalDate.now(TimeUtil.UTC_PLUS_7).minusDays(1);
        AttendanceRecord record = makeRecord(yesterday, null);

        when(attendanceRecordRepository
            .findByCheckInTimeAfterAndCheckOutTimeIsNullAndAttendanceStatusNotIn(any(), any()))
            .thenReturn(List.of(record));
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        incompleteAttendanceJob.markIncompleteRecords();

        assertThat(record.getAttendanceStatus()).isEqualTo(AttendanceStatus.INCOMPLETE);
        verify(attendanceRecordRepository).save(record);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markIncompleteRecords_pastDayRecord_notificationContainsDate() {
        LocalDate yesterday = LocalDate.now(TimeUtil.UTC_PLUS_7).minusDays(1);
        AttendanceRecord record = makeRecord(yesterday, null);

        when(attendanceRecordRepository
            .findByCheckInTimeAfterAndCheckOutTimeIsNullAndAttendanceStatusNotIn(any(), any()))
            .thenReturn(List.of(record));
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        incompleteAttendanceJob.markIncompleteRecords();

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notifCaptor.capture());
        Notification notification = notifCaptor.getValue();
        assertThat(notification.getRecipient()).isEqualTo(employee);
        assertThat(notification.getType()).isEqualTo(NotificationType.INCOMPLETE_RECORD);
        assertThat(notification.getMessage()).contains(yesterday.toString());
        assertThat(notification.getReferenceId()).isEqualTo("rec-1");
    }

    @Test
    void markIncompleteRecords_multiplePastDayRecords_allMarkedIncomplete() {
        LocalDate yesterday = LocalDate.now(TimeUtil.UTC_PLUS_7).minusDays(1);
        AttendanceRecord rec1 = makeRecord(yesterday, null);
        rec1.setId("rec-1");
        AttendanceRecord rec2 = makeRecord(yesterday.minusDays(1), null);
        rec2.setId("rec-2");

        when(attendanceRecordRepository
            .findByCheckInTimeAfterAndCheckOutTimeIsNullAndAttendanceStatusNotIn(any(), any()))
            .thenReturn(List.of(rec1, rec2));
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        incompleteAttendanceJob.markIncompleteRecords();

        assertThat(rec1.getAttendanceStatus()).isEqualTo(AttendanceStatus.INCOMPLETE);
        assertThat(rec2.getAttendanceStatus()).isEqualTo(AttendanceStatus.INCOMPLETE);
        verify(attendanceRecordRepository, times(2)).save(any());
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    // ── Sub-status skip logic ──────────────────────────────────────────────

    @Test
    void markIncompleteRecords_pendingAdjustmentSubStatus_skipped() {
        LocalDate yesterday = LocalDate.now(TimeUtil.UTC_PLUS_7).minusDays(1);
        AttendanceRecord record = makeRecord(yesterday, ApprovalSubStatus.PENDING_ADJUSTMENT);

        when(attendanceRecordRepository
            .findByCheckInTimeAfterAndCheckOutTimeIsNullAndAttendanceStatusNotIn(any(), any()))
            .thenReturn(List.of(record));

        incompleteAttendanceJob.markIncompleteRecords();

        assertThat(record.getAttendanceStatus()).isEqualTo(AttendanceStatus.ON_TIME);
        verify(attendanceRecordRepository, never()).save(any());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void markIncompleteRecords_pendingApprovalSubStatus_skipped() {
        LocalDate yesterday = LocalDate.now(TimeUtil.UTC_PLUS_7).minusDays(1);
        AttendanceRecord record = makeRecord(yesterday, ApprovalSubStatus.PENDING_APPROVAL);

        when(attendanceRecordRepository
            .findByCheckInTimeAfterAndCheckOutTimeIsNullAndAttendanceStatusNotIn(any(), any()))
            .thenReturn(List.of(record));

        incompleteAttendanceJob.markIncompleteRecords();

        assertThat(record.getAttendanceStatus()).isEqualTo(AttendanceStatus.ON_TIME);
        verify(attendanceRecordRepository, never()).save(any());
        verifyNoInteractions(notificationRepository);
    }

    // ── Today's record — time-based grace period ──────────────────────────

    @Test
    void markIncompleteRecords_todayRecordPastGracePeriod_isMarkedIncomplete() {
        // Fix "now" to 19:00 VN — past grace cutoff 18:00
        LocalDate fixedDate = LocalDate.of(2026, 6, 30);
        LocalDateTime fixedNowVn = LocalDateTime.of(2026, 6, 30, 19, 0);

        AttendanceRecord record = makeRecord(fixedDate, null);

        try (MockedStatic<TimeUtil> mockedTimeUtil = Mockito.mockStatic(TimeUtil.class)) {
            mockedTimeUtil.when(TimeUtil::nowUtcPlus7).thenReturn(fixedNowVn);
            mockedTimeUtil.when(() -> TimeUtil.toUtcPlus7(any())).thenCallRealMethod();
            mockedTimeUtil.when(() -> TimeUtil.toUtc(any())).thenCallRealMethod();

            when(attendanceRecordRepository
                .findByCheckInTimeAfterAndCheckOutTimeIsNullAndAttendanceStatusNotIn(any(), any()))
                .thenReturn(List.of(record));
            when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            incompleteAttendanceJob.markIncompleteRecords();

            assertThat(record.getAttendanceStatus()).isEqualTo(AttendanceStatus.INCOMPLETE);
            verify(notificationRepository).save(any(Notification.class));
        }
    }

    @Test
    void markIncompleteRecords_todayRecordBeforeGracePeriod_notMarked() {
        // Fix "now" to 17:00 VN — before grace cutoff 18:00
        LocalDate fixedDate = LocalDate.of(2026, 6, 30);
        LocalDateTime fixedNowVn = LocalDateTime.of(2026, 6, 30, 17, 0);

        AttendanceRecord record = makeRecord(fixedDate, null);

        try (MockedStatic<TimeUtil> mockedTimeUtil = Mockito.mockStatic(TimeUtil.class)) {
            mockedTimeUtil.when(TimeUtil::nowUtcPlus7).thenReturn(fixedNowVn);
            mockedTimeUtil.when(() -> TimeUtil.toUtcPlus7(any())).thenCallRealMethod();
            mockedTimeUtil.when(() -> TimeUtil.toUtc(any())).thenCallRealMethod();

            when(attendanceRecordRepository
                .findByCheckInTimeAfterAndCheckOutTimeIsNullAndAttendanceStatusNotIn(any(), any()))
                .thenReturn(List.of(record));

            incompleteAttendanceJob.markIncompleteRecords();

            assertThat(record.getAttendanceStatus()).isEqualTo(AttendanceStatus.ON_TIME);
            verify(attendanceRecordRepository, never()).save(any());
            verifyNoInteractions(notificationRepository);
        }
    }

    // ── Empty candidate list ───────────────────────────────────────────────

    @Test
    void markIncompleteRecords_noCandidates_noSaveCalls() {
        when(attendanceRecordRepository
            .findByCheckInTimeAfterAndCheckOutTimeIsNullAndAttendanceStatusNotIn(any(), any()))
            .thenReturn(List.of());

        incompleteAttendanceJob.markIncompleteRecords();

        verify(attendanceRecordRepository, never()).save(any());
        verifyNoInteractions(notificationRepository);
    }

    // ── Shift null guard ───────────────────────────────────────────────────

    @Test
    void markIncompleteRecords_todayRecordWithNullShift_notMarked() {
        // Fix "now" to 19:00 VN — would be past grace period, but shift is null → skip
        LocalDate fixedDate = LocalDate.of(2026, 6, 30);
        LocalDateTime fixedNowVn = LocalDateTime.of(2026, 6, 30, 19, 0);

        AttendanceRecord record = makeRecord(fixedDate, null);
        record.setShift(null);

        try (MockedStatic<TimeUtil> mockedTimeUtil = Mockito.mockStatic(TimeUtil.class)) {
            mockedTimeUtil.when(TimeUtil::nowUtcPlus7).thenReturn(fixedNowVn);

            when(attendanceRecordRepository
                .findByCheckInTimeAfterAndCheckOutTimeIsNullAndAttendanceStatusNotIn(any(), any()))
                .thenReturn(List.of(record));

            incompleteAttendanceJob.markIncompleteRecords();

            verify(attendanceRecordRepository, never()).save(any());
            verifyNoInteractions(notificationRepository);
        }
    }
}
