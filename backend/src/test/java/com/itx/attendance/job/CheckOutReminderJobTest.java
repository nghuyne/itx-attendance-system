package com.itx.attendance.job;

import com.itx.attendance.domain.*;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.HolidayRepository;
import com.itx.attendance.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Story 6.2 — CheckOutReminderJob (GAP-03).
 *
 * Runs weekdays at 17:30 (Asia/Ho_Chi_Minh) and emails employees who checked
 * in but haven't checked out yet, excluding records already INCOMPLETE or
 * on a holiday.
 */
@ExtendWith(MockitoExtension.class)
class CheckOutReminderJobTest {

    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private CheckOutReminderJob checkOutReminderJob;

    private Shift shift;
    private User employee;

    @BeforeEach
    void setUp() {
        shift = Shift.builder()
            .id("shift-1")
            .name("Ca Sáng")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .build();

        employee = User.builder()
            .id("emp-1")
            .username("emp1")
            .fullName("Nguyen Van A")
            .email("emp1@itx.local")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build();
    }

    private AttendanceRecord candidateRecord() {
        return AttendanceRecord.builder()
            .id("rec-1")
            .employee(employee)
            .shift(shift)
            .date(LocalDate.now())
            .checkInTime(LocalDateTime.now().minusHours(9))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build();
    }

    // ── Holiday short-circuit ────────────────────────────────────────────────

    @Test
    void sendCheckOutReminders_holiday_exitsEarlyWithoutQueryingRecords() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(true);

        checkOutReminderJob.sendCheckOutReminders();

        verify(attendanceRecordRepository, never())
            .findByDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNullAndAttendanceStatusNot(any(), any());
        verify(emailService, never()).sendEmailAsync(any(), any(), any());
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void sendCheckOutReminders_checkedInNoCheckOut_sendsReminderEmail() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(attendanceRecordRepository
            .findByDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNullAndAttendanceStatusNot(
                any(LocalDate.class), eq(AttendanceStatus.INCOMPLETE)))
            .thenReturn(List.of(candidateRecord()));

        checkOutReminderJob.sendCheckOutReminders();

        verify(emailService).sendEmailAsync(
            eq(employee),
            eq("[ITX Chấm công] Nhắc nhở: Bạn chưa check-out hôm nay"),
            contains("Nguyen Van A"));
    }

    // ── No candidates ─────────────────────────────────────────────────────────

    @Test
    void sendCheckOutReminders_noCandidates_sendsNoEmails() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(attendanceRecordRepository
            .findByDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNullAndAttendanceStatusNot(
                any(LocalDate.class), eq(AttendanceStatus.INCOMPLETE)))
            .thenReturn(List.of());

        checkOutReminderJob.sendCheckOutReminders();

        verify(emailService, never()).sendEmailAsync(any(), any(), any());
    }

    // ── Query excludes INCOMPLETE records already ────────────────────────────

    @Test
    void sendCheckOutReminders_queriesWithIncompleteExclusion() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(attendanceRecordRepository
            .findByDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNullAndAttendanceStatusNot(
                any(LocalDate.class), eq(AttendanceStatus.INCOMPLETE)))
            .thenReturn(List.of());

        checkOutReminderJob.sendCheckOutReminders();

        verify(attendanceRecordRepository).findByDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNullAndAttendanceStatusNot(
            eq(LocalDate.now(com.itx.attendance.util.TimeUtil.UTC_PLUS_7)), eq(AttendanceStatus.INCOMPLETE));
    }

    // ── Multiple candidates ───────────────────────────────────────────────────

    @Test
    void sendCheckOutReminders_multipleCandidates_sendsEmailToEach() {
        User employee2 = User.builder()
            .id("emp-2")
            .username("emp2")
            .fullName("Tran Thi B")
            .email("emp2@itx.local")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build();
        AttendanceRecord record2 = AttendanceRecord.builder()
            .id("rec-2")
            .employee(employee2)
            .shift(shift)
            .date(LocalDate.now())
            .checkInTime(LocalDateTime.now().minusHours(9))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build();

        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(attendanceRecordRepository
            .findByDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNullAndAttendanceStatusNot(
                any(LocalDate.class), eq(AttendanceStatus.INCOMPLETE)))
            .thenReturn(List.of(candidateRecord(), record2));

        checkOutReminderJob.sendCheckOutReminders();

        verify(emailService).sendEmailAsync(eq(employee), any(), any());
        verify(emailService).sendEmailAsync(eq(employee2), any(), any());
    }

    // ── Resilience ────────────────────────────────────────────────────────────

    @Test
    void sendCheckOutReminders_emailServiceThrows_doesNotPropagate() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(attendanceRecordRepository
            .findByDateAndCheckInTimeIsNotNullAndCheckOutTimeIsNullAndAttendanceStatusNot(
                any(LocalDate.class), eq(AttendanceStatus.INCOMPLETE)))
            .thenReturn(List.of(candidateRecord()));
        doThrow(new RuntimeException("mail server down"))
            .when(emailService).sendEmailAsync(any(), any(), any());

        checkOutReminderJob.sendCheckOutReminders();
        // No exception propagated — failure is logged and swallowed
    }
}
