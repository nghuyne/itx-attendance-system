package com.itx.attendance.job;

import com.itx.attendance.domain.*;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Story 3.5 — AbsentRecordJob (FR-10).
 *
 * The job runs at 00:05 (VN) and creates ABSENT records for employees
 * who had a shift yesterday but no check-in. Uses `any()` for the date
 * param because "yesterday" is computed from the real clock.
 */
@ExtendWith(MockitoExtension.class)
class AbsentRecordJobTest {

    @Mock private UserRepository userRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;

    @InjectMocks
    private AbsentRecordJob absentRecordJob;

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
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build();
    }

    // ── Happy path ─────────────────────────────────────────────────────────

    @Test
    void createAbsentRecords_noCheckIn_createsAbsentRecord() {
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee));
        when(attendanceRecordRepository.existsByEmployeeIdAndDate(eq("emp-1"), any(LocalDate.class)))
            .thenReturn(false);
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        absentRecordJob.createAbsentRecords();

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRecordRepository).save(captor.capture());
        AttendanceRecord saved = captor.getValue();
        assertThat(saved.getAttendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(saved.getEmployee()).isEqualTo(employee);
        assertThat(saved.getShift()).isEqualTo(shift);
        assertThat(saved.getCheckInTime()).isNull();
        assertThat(saved.getCheckOutTime()).isNull();
    }

    @Test
    void createAbsentRecords_absentRecordDate_isYesterday() {
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee));
        when(attendanceRecordRepository.existsByEmployeeIdAndDate(eq("emp-1"), any(LocalDate.class)))
            .thenReturn(false);
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate expectedYesterday = LocalDate.now(com.itx.attendance.util.TimeUtil.UTC_PLUS_7).minusDays(1);

        absentRecordJob.createAbsentRecords();

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getDate()).isEqualTo(expectedYesterday);
    }

    // ── Skip existing record ───────────────────────────────────────────────

    @Test
    void createAbsentRecords_existingCheckInRecord_skipped() {
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee));
        when(attendanceRecordRepository.existsByEmployeeIdAndDate(eq("emp-1"), any(LocalDate.class)))
            .thenReturn(true);

        absentRecordJob.createAbsentRecords();

        verify(attendanceRecordRepository, never()).save(any());
    }

    // ── Multiple employees ─────────────────────────────────────────────────

    @Test
    void createAbsentRecords_multipleEmployees_createsRecordOnlyForAbsent() {
        User employee2 = User.builder()
            .id("emp-2")
            .username("emp2")
            .fullName("Tran Thi B")
            .role(UserRole.EMPLOYEE)
            .shift(shift)
            .build();

        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee, employee2));
        // emp-1 has no record → absent; emp-2 has a record → skip
        when(attendanceRecordRepository.existsByEmployeeIdAndDate(eq("emp-1"), any(LocalDate.class)))
            .thenReturn(false);
        when(attendanceRecordRepository.existsByEmployeeIdAndDate(eq("emp-2"), any(LocalDate.class)))
            .thenReturn(true);
        when(attendanceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        absentRecordJob.createAbsentRecords();

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRecordRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEmployee().getId()).isEqualTo("emp-1");
    }

    // ── Empty employee list ────────────────────────────────────────────────

    @Test
    void createAbsentRecords_noActiveEmployeesWithShift_createsNoRecords() {
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of());

        absentRecordJob.createAbsentRecords();

        verify(attendanceRecordRepository, never()).save(any());
        verify(attendanceRecordRepository, never()).existsByEmployeeIdAndDate(any(), any());
    }

    // ── Duplicate race condition ───────────────────────────────────────────

    @Test
    void createAbsentRecords_duplicateEntryViolation_handledGracefully() {
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee));
        when(attendanceRecordRepository.existsByEmployeeIdAndDate(any(), any()))
            .thenReturn(false);

        // Simulate a race condition where another thread inserted the row first
        RuntimeException duplicateEx = new DataIntegrityViolationException("Duplicate entry",
            new RuntimeException("Duplicate entry"));
        when(attendanceRecordRepository.save(any())).thenThrow(duplicateEx);

        absentRecordJob.createAbsentRecords();
        // No exception propagated — duplicate is handled gracefully
    }

    @Test
    void createAbsentRecords_fkConstraintViolation_loggedGracefully() {
        when(userRepository.findByRoleAndActiveTrueAndShiftIsNotNull(UserRole.EMPLOYEE))
            .thenReturn(List.of(employee));
        when(attendanceRecordRepository.existsByEmployeeIdAndDate(any(), any()))
            .thenReturn(false);

        // FK violations ("foreign key" in cause) are logged as WARN but not re-thrown
        RuntimeException fkEx = new DataIntegrityViolationException("FK constraint violation",
            new RuntimeException("foreign key constraint fails"));
        when(attendanceRecordRepository.save(any())).thenThrow(fkEx);

        absentRecordJob.createAbsentRecords();
        // Completes without propagating the exception
    }
}
