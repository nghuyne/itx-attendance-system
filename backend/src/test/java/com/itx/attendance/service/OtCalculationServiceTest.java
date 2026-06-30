package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.repository.HolidayRepository;
import com.itx.attendance.repository.OtRecordRepository;
import com.itx.attendance.repository.OtRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Story 3.6 — OT Calculation & Multiplier Classification (FR-15, FR-16).
 *
 * Shift: 08:00–17:00, ot_buffer=30 min → OT cutoff = 17:30 VN.
 * All checkOutTime values are UTC; checkout at 11:00 UTC = 18:00 VN → 30 min OT.
 */
@ExtendWith(MockitoExtension.class)
class OtCalculationServiceTest {

    @Mock private OtRecordRepository otRecordRepository;
    @Mock private OtRequestRepository otRequestRepository;
    @Mock private HolidayRepository holidayRepository;

    @InjectMocks
    private OtCalculationService otCalculationService;

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

        shift = Shift.builder()
            .id("shift-1")
            .name("Ca Sáng")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .otBuffer(30)
            .build();
    }

    private AttendanceRecord makeRecord(AttendanceStatus status, LocalDateTime checkOutUtc, LocalDate date) {
        return AttendanceRecord.builder()
            .id("rec-1")
            .employee(employee)
            .shift(shift)
            .date(date)
            .checkInTime(date.atTime(1, 0))
            .checkOutTime(checkOutUtc)
            .checkInIp("127.0.0.1")
            .checkInPhotoUrl("photo.jpg")
            .attendanceStatus(status)
            .build();
    }

    // ── Guard conditions ───────────────────────────────────────────────────

    @Test
    void calculateAndSave_statusINCOMPLETE_returnsEmpty() {
        AttendanceRecord record = makeRecord(
            AttendanceStatus.INCOMPLETE,
            LocalDateTime.of(2026, 6, 30, 11, 0),
            LocalDate.of(2026, 6, 30));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isEmpty();
        verifyNoInteractions(otRecordRepository);
    }

    @Test
    void calculateAndSave_statusABSENT_returnsEmpty() {
        AttendanceRecord record = makeRecord(
            AttendanceStatus.ABSENT, null, LocalDate.of(2026, 6, 30));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isEmpty();
        verifyNoInteractions(otRecordRepository);
    }

    @Test
    void calculateAndSave_noCheckoutTime_returnsEmpty() {
        AttendanceRecord record = makeRecord(
            AttendanceStatus.ON_TIME, null, LocalDate.of(2026, 6, 30));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isEmpty();
        verifyNoInteractions(otRecordRepository);
    }

    @Test
    void calculateAndSave_shiftIsNull_returnsEmpty() {
        AttendanceRecord record = makeRecord(
            AttendanceStatus.ON_TIME,
            LocalDateTime.of(2026, 6, 30, 11, 0),
            LocalDate.of(2026, 6, 30));
        record.setShift(null);

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isEmpty();
        verifyNoInteractions(otRecordRepository);
    }

    @Test
    void calculateAndSave_checkoutBeforeOtCutoff_returnsEmpty() {
        // 10:00 UTC = 17:00 VN — exactly at shift end, before 17:30 cutoff
        LocalDateTime checkoutUtc = LocalDateTime.of(2026, 6, 30, 10, 0);
        AttendanceRecord record = makeRecord(
            AttendanceStatus.ON_TIME, checkoutUtc, LocalDate.of(2026, 6, 30));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isEmpty();
        verifyNoInteractions(otRecordRepository);
    }

    @Test
    void calculateAndSave_checkoutExactlyAtCutoff_returnsEmpty() {
        // 10:30 UTC = 17:30 VN — exactly at OT cutoff (0 minutes OT)
        LocalDateTime checkoutUtc = LocalDateTime.of(2026, 6, 30, 10, 30);
        AttendanceRecord record = makeRecord(
            AttendanceStatus.ON_TIME, checkoutUtc, LocalDate.of(2026, 6, 30));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isEmpty();
        verifyNoInteractions(otRecordRepository);
    }

    // ── Day type classification ────────────────────────────────────────────

    @Test
    void calculateAndSave_weekdayOT_creates1_5xRecord() {
        // 2026-06-30 is a Tuesday (WEEKDAY); checkout 11:00 UTC = 18:00 VN → 30 min OT
        LocalDate weekday = LocalDate.of(2026, 6, 30);
        LocalDateTime checkoutUtc = LocalDateTime.of(2026, 6, 30, 11, 0);
        AttendanceRecord record = makeRecord(AttendanceStatus.ON_TIME, checkoutUtc, weekday);

        when(holidayRepository.existsByDate(weekday)).thenReturn(false);
        when(otRequestRepository.findFirstByEmployeeIdAndPlannedDateAndStatus(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(otRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isPresent();
        OtRecord ot = result.get();
        assertThat(ot.getDayType()).isEqualTo(DayType.WEEKDAY);
        assertThat(ot.getOtMultiplier()).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(ot.getOtDurationMinutes()).isEqualTo(30);
        assertThat(ot.getEmployee()).isEqualTo(employee);
    }

    @Test
    void calculateAndSave_weekendOT_creates2_0xRecord() {
        // 2026-06-28 is a Sunday (WEEKEND)
        LocalDate weekend = LocalDate.of(2026, 6, 28);
        LocalDateTime checkoutUtc = LocalDateTime.of(2026, 6, 28, 11, 0);
        AttendanceRecord record = makeRecord(AttendanceStatus.ON_TIME, checkoutUtc, weekend);

        when(holidayRepository.existsByDate(weekend)).thenReturn(false);
        when(otRequestRepository.findFirstByEmployeeIdAndPlannedDateAndStatus(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(otRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isPresent();
        assertThat(result.get().getDayType()).isEqualTo(DayType.WEEKEND);
        assertThat(result.get().getOtMultiplier()).isEqualByComparingTo(new BigDecimal("2.0"));
    }

    @Test
    void calculateAndSave_holidayOT_creates3_0xRecord() {
        // 2026-09-02 is National Day (HOLIDAY)
        LocalDate holiday = LocalDate.of(2026, 9, 2);
        LocalDateTime checkoutUtc = LocalDateTime.of(2026, 9, 2, 11, 0);
        AttendanceRecord record = makeRecord(AttendanceStatus.ON_TIME, checkoutUtc, holiday);

        when(holidayRepository.existsByDate(holiday)).thenReturn(true);
        when(otRequestRepository.findFirstByEmployeeIdAndPlannedDateAndStatus(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(otRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isPresent();
        assertThat(result.get().getDayType()).isEqualTo(DayType.HOLIDAY);
        assertThat(result.get().getOtMultiplier()).isEqualByComparingTo(new BigDecimal("3.0"));
    }

    @Test
    void calculateAndSave_weekendAndHoliday_holidayTakesPriority() {
        // 2026-05-02 is a Saturday (WEEKEND) — treat as HOLIDAY to verify priority
        LocalDate weekendHoliday = LocalDate.of(2026, 5, 2);
        LocalDateTime checkoutUtc = LocalDateTime.of(2026, 5, 2, 11, 0);
        AttendanceRecord record = makeRecord(AttendanceStatus.ON_TIME, checkoutUtc, weekendHoliday);

        when(holidayRepository.existsByDate(weekendHoliday)).thenReturn(true);
        when(otRequestRepository.findFirstByEmployeeIdAndPlannedDateAndStatus(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(otRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isPresent();
        assertThat(result.get().getDayType()).isEqualTo(DayType.HOLIDAY);
        assertThat(result.get().getOtMultiplier()).isEqualByComparingTo(new BigDecimal("3.0"));
    }

    // ── OT duration calculation ────────────────────────────────────────────

    @Test
    void calculateAndSave_60MinuteOT_correctDuration() {
        // Checkout at 11:30 UTC = 18:30 VN → 60 min OT (cutoff 17:30)
        LocalDate weekday = LocalDate.of(2026, 6, 30);
        LocalDateTime checkoutUtc = LocalDateTime.of(2026, 6, 30, 11, 30);
        AttendanceRecord record = makeRecord(AttendanceStatus.ON_TIME, checkoutUtc, weekday);

        when(holidayRepository.existsByDate(weekday)).thenReturn(false);
        when(otRequestRepository.findFirstByEmployeeIdAndPlannedDateAndStatus(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(otRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isPresent();
        assertThat(result.get().getOtDurationMinutes()).isEqualTo(60);
    }

    // ── OT Request linking ─────────────────────────────────────────────────

    @Test
    void calculateAndSave_linksApprovedOtRequest() {
        LocalDate weekday = LocalDate.of(2026, 6, 30);
        LocalDateTime checkoutUtc = LocalDateTime.of(2026, 6, 30, 11, 0);
        AttendanceRecord record = makeRecord(AttendanceStatus.ON_TIME, checkoutUtc, weekday);

        OtRequest approvedRequest = OtRequest.builder()
            .id("ot-req-1")
            .employee(employee)
            .plannedDate(weekday)
            .status(RequestStatus.APPROVED)
            .reason("Làm thêm dự án")
            .build();

        when(holidayRepository.existsByDate(weekday)).thenReturn(false);
        when(otRequestRepository.findFirstByEmployeeIdAndPlannedDateAndStatus(
            "emp-1", weekday, RequestStatus.APPROVED))
            .thenReturn(Optional.of(approvedRequest));
        when(otRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isPresent();
        assertThat(result.get().getOtRequest()).isEqualTo(approvedRequest);
    }

    @Test
    void calculateAndSave_noApprovedOtRequest_otRequestIsNull() {
        LocalDate weekday = LocalDate.of(2026, 6, 30);
        LocalDateTime checkoutUtc = LocalDateTime.of(2026, 6, 30, 11, 0);
        AttendanceRecord record = makeRecord(AttendanceStatus.ON_TIME, checkoutUtc, weekday);

        when(holidayRepository.existsByDate(weekday)).thenReturn(false);
        when(otRequestRepository.findFirstByEmployeeIdAndPlannedDateAndStatus(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(otRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<OtRecord> result = otCalculationService.calculateAndSave(record);

        assertThat(result).isPresent();
        assertThat(result.get().getOtRequest()).isNull();
    }

    // ── Recalculate ────────────────────────────────────────────────────────

    @Test
    void recalculate_deletesExistingAndCreatesNewOtRecord() {
        LocalDate weekday = LocalDate.of(2026, 6, 30);
        LocalDateTime checkoutUtc = LocalDateTime.of(2026, 6, 30, 11, 0);
        AttendanceRecord record = makeRecord(AttendanceStatus.ON_TIME, checkoutUtc, weekday);

        when(holidayRepository.existsByDate(weekday)).thenReturn(false);
        when(otRequestRepository.findFirstByEmployeeIdAndPlannedDateAndStatus(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(otRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<OtRecord> result = otCalculationService.recalculate(record);

        verify(otRecordRepository).deleteByAttendanceRecordId("rec-1");
        assertThat(result).isPresent();
        assertThat(result.get().getDayType()).isEqualTo(DayType.WEEKDAY);
    }

    @Test
    void recalculate_incompleteRecord_deletesExistingAndReturnsEmpty() {
        AttendanceRecord record = makeRecord(
            AttendanceStatus.INCOMPLETE,
            LocalDateTime.of(2026, 6, 30, 11, 0),
            LocalDate.of(2026, 6, 30));

        Optional<OtRecord> result = otCalculationService.recalculate(record);

        verify(otRecordRepository).deleteByAttendanceRecordId("rec-1");
        assertThat(result).isEmpty();
    }
}
