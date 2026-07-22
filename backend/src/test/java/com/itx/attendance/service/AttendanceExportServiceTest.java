package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.OtRecordRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Epic 7 — Story 7.2: Excel Export Attendance Report.
 *
 * Verifies the generated XSSFWorkbook content directly (headers, translated status/day-of-week,
 * working-hour computation, OT lookup, and edge cases like empty result set and negative duration).
 */
@ExtendWith(MockitoExtension.class)
class AttendanceExportServiceTest {

    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private OtRecordRepository otRecordRepository;

    @InjectMocks
    private AttendanceExportService attendanceExportService;

    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    private User employee(String id, String username, String fullName) {
        return User.builder().id(id).username(username).fullName(fullName).role(UserRole.EMPLOYEE).build();
    }

    // ── Sheet name & header ──────────────────────────────────────────────────

    @Test
    void exportToExcel_noRecords_returnsHeaderOnlySheet() throws IOException {
        when(attendanceRecordRepository.findByDateBetweenOrderByDateDesc(FROM, TO)).thenReturn(List.of());
        when(otRecordRepository.findForExport(FROM, TO, null)).thenReturn(List.of());

        byte[] bytes = attendanceExportService.exportToExcel(FROM, TO, null);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(workbook.getSheetName(0)).isEqualTo("Bảng công");
            assertThat(sheet.getLastRowNum()).isEqualTo(0);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("STT");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Mã NV");
            assertThat(header.getCell(11).getStringCellValue()).isEqualTo("Ghi chú");
        }
    }

    // ── Data row mapping ───────────────────────────────────────────────────

    @Test
    void exportToExcel_recordWithCheckInAndOut_computesWorkingHours() throws IOException {
        AttendanceRecord record = AttendanceRecord.builder()
            .id("rec-1")
            .employee(employee("emp-1", "emp1", "Nguyen Van A"))
            .date(LocalDate.of(2026, 6, 15))
            .checkInTime(LocalDateTime.of(2026, 6, 15, 1, 0))
            .checkOutTime(LocalDateTime.of(2026, 6, 15, 9, 0))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build();

        when(attendanceRecordRepository.findByDateBetweenOrderByDateDesc(FROM, TO)).thenReturn(List.of(record));
        when(otRecordRepository.findForExport(FROM, TO, null)).thenReturn(List.of());

        byte[] bytes = attendanceExportService.exportToExcel(FROM, TO, null);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("emp1");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("Nguyen Van A");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("15/06/2026");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("Thứ 2");
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("Đúng giờ");
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("8.0");
            assertThat(row.getCell(9).getStringCellValue()).isEqualTo("0.0");
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("");
            assertThat(row.getCell(11).getStringCellValue()).isEqualTo("");
        }
    }

    @Test
    void exportToExcel_recordWithoutCheckOut_writesEmDashForWorkingHours() throws IOException {
        AttendanceRecord record = AttendanceRecord.builder()
            .id("rec-2")
            .employee(employee("emp-2", "emp2", "Tran Thi B"))
            .date(LocalDate.of(2026, 6, 16))
            .checkInTime(LocalDateTime.of(2026, 6, 16, 1, 0))
            .checkOutTime(null)
            .attendanceStatus(AttendanceStatus.INCOMPLETE)
            .build();

        when(attendanceRecordRepository.findByDateBetweenOrderByDateDesc(FROM, TO)).thenReturn(List.of(record));
        when(otRecordRepository.findForExport(FROM, TO, null)).thenReturn(List.of());

        byte[] bytes = attendanceExportService.exportToExcel(FROM, TO, null);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("");
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("Thiếu checkout");
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("—");
        }
    }

    @Test
    void exportToExcel_checkOutBeforeCheckIn_writesEmDashInsteadOfNegativeHours() throws IOException {
        AttendanceRecord record = AttendanceRecord.builder()
            .id("rec-3")
            .employee(employee("emp-3", "emp3", "Le Van C"))
            .date(LocalDate.of(2026, 6, 17))
            .checkInTime(LocalDateTime.of(2026, 6, 17, 9, 0))
            .checkOutTime(LocalDateTime.of(2026, 6, 17, 1, 0))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build();

        when(attendanceRecordRepository.findByDateBetweenOrderByDateDesc(FROM, TO)).thenReturn(List.of(record));
        when(otRecordRepository.findForExport(FROM, TO, null)).thenReturn(List.of());

        byte[] bytes = attendanceExportService.exportToExcel(FROM, TO, null);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("—");
        }
    }

    @Test
    void exportToExcel_excusedStatus_translatesToVietnamese() throws IOException {
        AttendanceRecord record = AttendanceRecord.builder()
            .id("rec-4")
            .employee(employee("emp-4", "emp4", "Pham Thi D"))
            .date(LocalDate.of(2026, 6, 18))
            .checkInTime(null)
            .checkOutTime(null)
            .attendanceStatus(AttendanceStatus.EXCUSED)
            .build();

        when(attendanceRecordRepository.findByDateBetweenOrderByDateDesc(FROM, TO)).thenReturn(List.of(record));
        when(otRecordRepository.findForExport(FROM, TO, null)).thenReturn(List.of());

        byte[] bytes = attendanceExportService.exportToExcel(FROM, TO, null);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("Được miễn");
        }
    }

    @Test
    void exportToExcel_adminOverrideRecord_writesNoteColumn() throws IOException {
        AttendanceRecord record = AttendanceRecord.builder()
            .id("rec-5")
            .employee(employee("emp-5", "emp5", "Hoang Van E"))
            .date(LocalDate.of(2026, 6, 19))
            .checkInTime(LocalDateTime.of(2026, 6, 19, 1, 0))
            .checkOutTime(LocalDateTime.of(2026, 6, 19, 9, 0))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .adminOverride(true)
            .build();

        when(attendanceRecordRepository.findByDateBetweenOrderByDateDesc(FROM, TO)).thenReturn(List.of(record));
        when(otRecordRepository.findForExport(FROM, TO, null)).thenReturn(List.of());

        byte[] bytes = attendanceExportService.exportToExcel(FROM, TO, null);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(11).getStringCellValue()).isEqualTo("Admin override");
        }
    }

    @Test
    void exportToExcel_recordWithOtRecord_writesOtHoursAndMultiplier() throws IOException {
        AttendanceRecord record = AttendanceRecord.builder()
            .id("rec-6")
            .employee(employee("emp-6", "emp6", "Vo Thi F"))
            .date(LocalDate.of(2026, 6, 20))
            .checkInTime(LocalDateTime.of(2026, 6, 20, 1, 0))
            .checkOutTime(LocalDateTime.of(2026, 6, 20, 11, 0))
            .attendanceStatus(AttendanceStatus.ON_TIME)
            .build();

        OtRecord otRecord = OtRecord.builder()
            .id("ot-1")
            .employee(record.getEmployee())
            .attendanceRecord(record)
            .date(record.getDate())
            .otDurationMinutes(90)
            .dayType(DayType.WEEKDAY)
            .otMultiplier(BigDecimal.valueOf(1.5))
            .build();

        when(attendanceRecordRepository.findByDateBetweenOrderByDateDesc(FROM, TO)).thenReturn(List.of(record));
        when(otRecordRepository.findForExport(FROM, TO, null)).thenReturn(List.of(otRecord));

        byte[] bytes = attendanceExportService.exportToExcel(FROM, TO, null);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(9).getStringCellValue()).isEqualTo("1.5");
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("1.5");
        }
    }

    // ── Employee filter ────────────────────────────────────────────────────

    // ── Large dataset ───────────────────────────────────────────────────────

    @Test
    void exportToExcel_largeDataset_writesAllRowsCorrectlyWithinTimeBudget() throws IOException {
        int recordCount = 5000;
        List<AttendanceRecord> records = new java.util.ArrayList<>(recordCount);
        List<OtRecord> otRecords = new java.util.ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            AttendanceRecord record = AttendanceRecord.builder()
                .id("rec-" + i)
                .employee(employee("emp-" + i, "emp" + i, "Employee " + i))
                .date(FROM.plusDays(i % 30))
                .checkInTime(LocalDateTime.of(2026, 6, 1 + (i % 28), 1, 0))
                .checkOutTime(LocalDateTime.of(2026, 6, 1 + (i % 28), 9, 0))
                .attendanceStatus(AttendanceStatus.ON_TIME)
                .build();
            records.add(record);
            // Every third record has an OT entry — exercises the otMap join at scale,
            // not just the happy path of one-record-at-a-time (see the 6 tests above).
            if (i % 3 == 0) {
                otRecords.add(OtRecord.builder()
                    .id("ot-" + i)
                    .employee(record.getEmployee())
                    .attendanceRecord(record)
                    .date(record.getDate())
                    .otDurationMinutes(60)
                    .dayType(DayType.WEEKDAY)
                    .otMultiplier(BigDecimal.valueOf(1.5))
                    .build());
            }
        }

        when(attendanceRecordRepository.findByDateBetweenOrderByDateDesc(FROM, TO)).thenReturn(records);
        when(otRecordRepository.findForExport(FROM, TO, null)).thenReturn(otRecords);

        long start = System.nanoTime();
        byte[] bytes = attendanceExportService.exportToExcel(FROM, TO, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Generous regression-guard bound, not a tight perf gate — CI runners vary and
        // POI's autoSizeColumn (AWT font metrics over every cell) dominates this for large
        // sheets. Catches an accidental algorithmic regression (e.g. swapping the otMap for
        // a linear scan) long before it would matter, without flaking on normal variance.
        assertThat(elapsedMs).isLessThan(30_000);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(recordCount);
            Row lastRow = sheet.getRow(recordCount);
            assertThat(lastRow.getCell(1).getStringCellValue()).isEqualTo("emp" + (recordCount - 1));
            // Row 3001 (i=3000, divisible by 3) must carry its OT figures from the join.
            Row otRow = sheet.getRow(3001);
            assertThat(otRow.getCell(9).getStringCellValue()).isEqualTo("1.0");
            assertThat(otRow.getCell(10).getStringCellValue()).isEqualTo("1.5");
        }
    }

    @Test
    void exportToExcel_withEmployeeId_usesFilteredRepositoryMethod() throws IOException {
        when(attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateDesc("emp-1", FROM, TO))
            .thenReturn(List.of());
        when(otRecordRepository.findForExport(FROM, TO, "emp-1")).thenReturn(List.of());

        attendanceExportService.exportToExcel(FROM, TO, "emp-1");

        org.mockito.Mockito.verify(attendanceRecordRepository)
            .findByEmployeeIdAndDateBetweenOrderByDateDesc("emp-1", FROM, TO);
        org.mockito.Mockito.verify(attendanceRecordRepository, org.mockito.Mockito.never())
            .findByDateBetweenOrderByDateDesc(any(), any());
    }

    @Test
    void exportToExcel_blankEmployeeId_treatedAsNoFilter() throws IOException {
        when(attendanceRecordRepository.findByDateBetweenOrderByDateDesc(FROM, TO)).thenReturn(List.of());
        when(otRecordRepository.findForExport(FROM, TO, null)).thenReturn(List.of());

        attendanceExportService.exportToExcel(FROM, TO, "   ");

        org.mockito.Mockito.verify(attendanceRecordRepository).findByDateBetweenOrderByDateDesc(FROM, TO);
        org.mockito.Mockito.verify(attendanceRecordRepository, org.mockito.Mockito.never())
            .findByEmployeeIdAndDateBetweenOrderByDateDesc(anyString(), any(), any());
    }
}
