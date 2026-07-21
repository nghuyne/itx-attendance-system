package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for R-004 (P1-003, test-design-qa.md) — OT record `ot_multiplier` and
 * `dayType` are a static snapshot taken at calculation time. A later change to holiday
 * config for the same date must NOT retroactively alter an already-persisted OtRecord;
 * only an explicit {@link OtCalculationService#recalculate} call may update it.
 *
 * Uses real repositories against H2 (not mocks) because the invariant under test is a
 * persistence-layer property: does the saved row change when unrelated holiday data changes?
 *
 * {@code @Transactional}: keeps setup + assertions in one Hibernate session so the lazily-fetched
 * {@code Shift} association stays initializable across the service's own {@code @Transactional}
 * boundaries; each test method still rolls back automatically, so no manual cleanup is needed.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:otcalcintegrationtestdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.password=test",
    "spring.flyway.enabled=false",
    "app.rate-limit.login.max-attempts=1000",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "minio.access-key=minioadmin",
    "minio.secret-key=minioadmin",
    "app.jwt.secret=test-secret-key-minimum-32-characters-abc",
    "app.jwt.access-token-expiration-ms=900000",
    "app.jwt.refresh-token-expiration-ms=604800000"
})
class OtCalculationServiceIntegrationTest {

    @Autowired private OtCalculationService otCalculationService;
    @Autowired private UserRepository userRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private OtRecordRepository otRecordRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User employee;
    private Shift shift;

    @BeforeEach
    void setUp() {
        otRecordRepository.deleteAll();
        attendanceRecordRepository.deleteAll();
        holidayRepository.deleteAll();
        userRepository.deleteAll();

        shift = shiftRepository.save(Shift.builder()
            .name("Ca Sáng")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0))
            .otBuffer(30)
            .build());

        employee = userRepository.save(User.builder()
            .username("ot_snapshot_emp")
            .email("ot_snapshot_emp@itx.local")
            .passwordHash(passwordEncoder.encode("emp123"))
            .fullName("OT Snapshot Employee")
            .role(UserRole.EMPLOYEE)
            .build());
    }

    @Test
    void otRecord_staysWeekdaySnapshot_afterHolidayAddedForSameDateLater() {
        // 2026-06-30 is a Tuesday (WEEKDAY) — no holiday exists yet.
        LocalDate date = LocalDate.of(2026, 6, 30);
        LocalDateTime checkOutUtc = LocalDateTime.of(2026, 6, 30, 11, 0); // 18:00 VN → 30 min OT

        AttendanceRecord record = attendanceRecordRepository.save(AttendanceRecord.builder()
            .employee(employee)
            .shift(shift)
            .date(date)
            .checkInTime(date.atTime(1, 0))
            .checkOutTime(checkOutUtc)
            .checkInIp("127.0.0.1")
            .checkInPhotoUrl("photo.jpg")
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build());

        Optional<OtRecord> created = otCalculationService.calculateAndSave(record);
        assertThat(created).isPresent();
        String otRecordId = created.get().getId();
        assertThat(created.get().getDayType()).isEqualTo(DayType.WEEKDAY);
        assertThat(created.get().getOtMultiplier()).isEqualByComparingTo(new BigDecimal("1.5"));

        // Later: an admin retroactively declares this date a holiday.
        holidayRepository.save(Holiday.builder()
            .date(date)
            .name("Retroactive Holiday")
            .type(HolidayType.FIXED)
            .year(2026)
            .build());

        // The already-persisted OtRecord must be unaffected — it is a snapshot, not a live view.
        OtRecord unchanged = otRecordRepository.findById(otRecordId).orElseThrow();
        assertThat(unchanged.getDayType()).isEqualTo(DayType.WEEKDAY);
        assertThat(unchanged.getOtMultiplier()).isEqualByComparingTo(new BigDecimal("1.5"));

        // Only an explicit recalculate() picks up the new holiday config.
        Optional<OtRecord> recalculated = otCalculationService.recalculate(record);
        assertThat(recalculated).isPresent();
        assertThat(recalculated.get().getDayType()).isEqualTo(DayType.HOLIDAY);
        assertThat(recalculated.get().getOtMultiplier()).isEqualByComparingTo(new BigDecimal("3.0"));
    }
}
