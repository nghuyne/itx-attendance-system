package com.itx.attendance.job;

import com.itx.attendance.domain.*;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.HolidayRepository;
import com.itx.attendance.repository.LeaveRequestRepository;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Story 6.2 — CheckInReminderJob (GAP-03).
 *
 * Runs weekdays at 8:15 AM (Asia/Ho_Chi_Minh) and emails employees who
 * haven't checked in yet, excluding those on approved leave or on a holiday.
 */
@ExtendWith(MockitoExtension.class)
class CheckInReminderJobTest {

    @Mock private UserRepository userRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private CheckInReminderJob checkInReminderJob;

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

    // ── Holiday short-circuit ────────────────────────────────────────────────

    @Test
    void sendCheckInReminders_holiday_exitsEarlyWithoutQueryingEmployees() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(true);

        checkInReminderJob.sendCheckInReminders();

        verify(userRepository, never()).findByRoleAndActiveTrueAndShiftIsNotNull(any());
        verify(emailService, never()).sendEmailAsync(any(), any(), any(), any(), any());
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void sendCheckInReminders_noCheckIn_sendsReminderEmail() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee));
        when(leaveRequestRepository.findApprovedEmployeeIdsForDate(any(LocalDate.class)))
            .thenReturn(Set.of());
        when(attendanceRecordRepository.existsByEmployeeIdAndDateAndCheckInTimeIsNotNull(eq("emp-1"), any(LocalDate.class)))
            .thenReturn(false);

        checkInReminderJob.sendCheckInReminders();

        verify(emailService).sendEmailAsync(
            any(),
            eq("emp-1"),
            eq("emp1@itx.local"),
            eq("[ITX Chấm công] Nhắc nhở: Bạn chưa check-in hôm nay"),
            contains("Nguyen Van A"));
    }

    // ── Exclusions ────────────────────────────────────────────────────────────

    @Test
    void sendCheckInReminders_alreadyCheckedIn_excluded() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee));
        when(leaveRequestRepository.findApprovedEmployeeIdsForDate(any(LocalDate.class)))
            .thenReturn(Set.of());
        when(attendanceRecordRepository.existsByEmployeeIdAndDateAndCheckInTimeIsNotNull(eq("emp-1"), any(LocalDate.class)))
            .thenReturn(true);

        checkInReminderJob.sendCheckInReminders();

        verify(emailService, never()).sendEmailAsync(any(), any(), any(), any(), any());
    }

    @Test
    void sendCheckInReminders_onApprovedLeave_excluded() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee));
        when(leaveRequestRepository.findApprovedEmployeeIdsForDate(any(LocalDate.class)))
            .thenReturn(Set.of("emp-1"));

        checkInReminderJob.sendCheckInReminders();

        verify(attendanceRecordRepository, never()).existsByEmployeeIdAndDateAndCheckInTimeIsNotNull(any(), any());
        verify(emailService, never()).sendEmailAsync(any(), any(), any(), any(), any());
    }

    @Test
    void sendCheckInReminders_noActiveEmployees_sendsNoEmails() {
        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of());
        when(leaveRequestRepository.findApprovedEmployeeIdsForDate(any(LocalDate.class)))
            .thenReturn(Set.of());

        checkInReminderJob.sendCheckInReminders();

        verify(emailService, never()).sendEmailAsync(any(), any(), any(), any(), any());
    }

    // ── Multiple employees ────────────────────────────────────────────────────

    @Test
    void sendCheckInReminders_multipleEmployees_onlySendsToQualifying() {
        User employee2 = User.builder()
            .id("emp-2")
            .username("emp2")
            .fullName("Tran Thi B")
            .email("emp2@itx.local")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build();

        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee, employee2));
        when(leaveRequestRepository.findApprovedEmployeeIdsForDate(any(LocalDate.class)))
            .thenReturn(Set.of());
        when(attendanceRecordRepository.existsByEmployeeIdAndDateAndCheckInTimeIsNotNull(eq("emp-1"), any(LocalDate.class)))
            .thenReturn(false);
        when(attendanceRecordRepository.existsByEmployeeIdAndDateAndCheckInTimeIsNotNull(eq("emp-2"), any(LocalDate.class)))
            .thenReturn(true);

        checkInReminderJob.sendCheckInReminders();

        verify(emailService, times(1)).sendEmailAsync(any(), eq("emp-1"), any(), any(), any());
        verify(emailService, never()).sendEmailAsync(any(), eq("emp-2"), any(), any(), any());
    }

    // ── Resilience ────────────────────────────────────────────────────────────

    @Test
    void sendCheckInReminders_emailServiceThrows_continuesForRemainingEmployees() {
        User employee2 = User.builder()
            .id("emp-2")
            .username("emp2")
            .fullName("Tran Thi B")
            .email("emp2@itx.local")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build();

        when(holidayRepository.existsByDate(any(LocalDate.class))).thenReturn(false);
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee, employee2));
        when(leaveRequestRepository.findApprovedEmployeeIdsForDate(any(LocalDate.class)))
            .thenReturn(Set.of());
        when(attendanceRecordRepository.existsByEmployeeIdAndDateAndCheckInTimeIsNotNull(any(), any(LocalDate.class)))
            .thenReturn(false);
        doThrow(new RuntimeException("mail server down"))
            .when(emailService).sendEmailAsync(any(), eq("emp-1"), any(), any(), any());

        checkInReminderJob.sendCheckInReminders();

        verify(emailService).sendEmailAsync(any(), eq("emp-2"), any(), any(), any());
    }
}
