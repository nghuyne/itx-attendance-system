package com.itx.attendance.service;

import com.itx.attendance.domain.AttendanceStatus;
import com.itx.attendance.domain.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 17);

    private AttendanceService attendanceService;
    private Shift shift;

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceService(
            null, null, null, null, null, null, null, null, null);
        shift = Shift.builder()
            .name("Day shift")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .lateInThreshold(10)
            .earlyOutThreshold(10)
            .halfDayThreshold(30)
            .build();
    }

    @Test
    void computeFinalStatus_onTime_returnsOnTime() {
        assertStatus(AttendanceStatus.ON_TIME, LocalTime.of(8, 0), LocalTime.of(17, 0));
    }

    @Test
    void computeFinalStatus_lateBeyondThreshold_returnsLateIn() {
        assertStatus(AttendanceStatus.LATE_IN, LocalTime.of(8, 11), LocalTime.of(17, 0));
    }

    @Test
    void computeFinalStatus_earlyOutBeyondThreshold_returnsEarlyOut() {
        assertStatus(AttendanceStatus.EARLY_OUT, LocalTime.of(8, 0), LocalTime.of(16, 49));
    }

    @Test
    void computeFinalStatus_bothOrdinaryViolations_returnsCombinedStatus() {
        assertStatus(AttendanceStatus.LATE_IN_EARLY_OUT, LocalTime.of(8, 11), LocalTime.of(16, 49));
    }

    @Test
    void computeFinalStatus_halfDayLateOverridesEarlyOut() {
        assertStatus(AttendanceStatus.HALF_DAY, LocalTime.of(8, 31), LocalTime.of(16, 49));
    }

    @Test
    void computeFinalStatus_halfDayEarlyOutOverridesLateIn() {
        assertStatus(AttendanceStatus.HALF_DAY, LocalTime.of(8, 11), LocalTime.of(16, 29));
    }

    @Test
    void computeFinalStatus_exactLateThreshold_isNotLate() {
        assertStatus(AttendanceStatus.ON_TIME, LocalTime.of(8, 10), LocalTime.of(17, 0));
    }

    @Test
    void computeFinalStatus_exactEarlyOutThreshold_isNotEarlyOut() {
        assertStatus(AttendanceStatus.ON_TIME, LocalTime.of(8, 0), LocalTime.of(16, 50));
    }

    @Test
    void computeFinalStatus_exactHalfDayLateThreshold_retainsLateIn() {
        assertStatus(AttendanceStatus.LATE_IN, LocalTime.of(8, 30), LocalTime.of(17, 0));
    }

    @Test
    void computeFinalStatus_exactHalfDayEarlyOutThreshold_retainsEarlyOut() {
        assertStatus(AttendanceStatus.EARLY_OUT, LocalTime.of(8, 0), LocalTime.of(16, 30));
    }

    @Test
    void computeFinalStatus_earlyCheckInAndLateCheckOut_areNotViolations() {
        assertStatus(AttendanceStatus.ON_TIME, LocalTime.of(7, 30), LocalTime.of(17, 30));
    }

    @Test
    void computeFinalStatus_nullCheckIn_returnsIncomplete() {
        assertThat(attendanceService.computeFinalStatus(null, utcFor(LocalTime.of(17, 0)), shift))
            .isEqualTo(AttendanceStatus.INCOMPLETE);
    }

    @Test
    void computeFinalStatus_nullCheckOut_returnsIncomplete() {
        assertThat(attendanceService.computeFinalStatus(utcFor(LocalTime.of(8, 0)), null, shift))
            .isEqualTo(AttendanceStatus.INCOMPLETE);
    }

    @Test
    void computeFinalStatus_nullShift_returnsIncomplete() {
        assertThat(attendanceService.computeFinalStatus(
            utcFor(LocalTime.of(8, 0)), utcFor(LocalTime.of(17, 0)), null))
            .isEqualTo(AttendanceStatus.INCOMPLETE);
    }

    private void assertStatus(AttendanceStatus expected, LocalTime checkInVn, LocalTime checkOutVn) {
        assertThat(attendanceService.computeFinalStatus(utcFor(checkInVn), utcFor(checkOutVn), shift))
            .isEqualTo(expected);
    }

    private LocalDateTime utcFor(LocalTime vietnamTime) {
        return DATE.atTime(vietnamTime).minusHours(7);
    }
}
