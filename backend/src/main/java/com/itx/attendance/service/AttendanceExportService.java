package com.itx.attendance.service;

import com.itx.attendance.domain.AttendanceRecord;
import com.itx.attendance.domain.AttendanceStatus;
import com.itx.attendance.domain.OtRecord;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.OtRecordRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceExportService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final OtRecordRepository otRecordRepository;

    @Transactional(readOnly = true)
    public byte[] exportToExcel(LocalDate from, LocalDate to, String employeeId) {
        String empId = (employeeId != null && !employeeId.isBlank()) ? employeeId : null;

        List<AttendanceRecord> records;
        if (empId != null) {
            records = attendanceRecordRepository
                .findByEmployeeIdAndDateBetweenOrderByDateDesc(empId, from, to);
        } else {
            records = attendanceRecordRepository.findByDateBetweenOrderByDateDesc(from, to);
        }

        Map<String, OtRecord> otMap = otRecordRepository
            .findForExport(from, to, empId)
            .stream()
            .collect(Collectors.toMap(
                o -> o.getAttendanceRecord().getId(),
                o -> o,
                (a, b) -> a));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Bảng công");
            writeHeaderRow(sheet);
            for (int i = 0; i < records.size(); i++) {
                writeDataRow(sheet, i + 1, records.get(i), otMap);
            }
            for (int col = 0; col < 12; col++) sheet.autoSizeColumn(col);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Không thể tạo file Excel", HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED");
        }
    }

    private void writeHeaderRow(XSSFSheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {"STT", "Mã NV", "Họ và tên", "Ngày", "Thứ", "Giờ vào", "Giờ ra",
                            "Trạng thái", "Giờ công", "Giờ OT", "Hệ số OT", "Ghi chú"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private void writeDataRow(XSSFSheet sheet, int rowNum, AttendanceRecord record, Map<String, OtRecord> otMap) {
        Row row = sheet.createRow(rowNum);
        OtRecord ot = otMap.get(record.getId());

        row.createCell(0).setCellValue(rowNum);
        row.createCell(1).setCellValue(record.getEmployee().getUsername());
        row.createCell(2).setCellValue(record.getEmployee().getFullName());
        row.createCell(3).setCellValue(record.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        row.createCell(4).setCellValue(translateDayOfWeek(record.getDate().getDayOfWeek()));
        row.createCell(5).setCellValue(formatTime(record.getCheckInTime()));
        row.createCell(6).setCellValue(formatTime(record.getCheckOutTime()));
        row.createCell(7).setCellValue(translateStatus(record.getAttendanceStatus()));

        if (record.getCheckInTime() != null && record.getCheckOutTime() != null) {
            double hours = Duration.between(record.getCheckInTime(), record.getCheckOutTime()).toMinutes() / 60.0;
            row.createCell(8).setCellValue(String.format("%.1f", hours));
        } else {
            row.createCell(8).setCellValue("—");
        }

        row.createCell(9).setCellValue(ot != null ? String.format("%.1f", ot.getOtDurationMinutes() / 60.0) : "0.0");
        row.createCell(10).setCellValue(ot != null ? ot.getOtMultiplier().toPlainString() : "");
        row.createCell(11).setCellValue(record.isAdminOverride() ? "Admin override" : "");
    }

    private String formatTime(LocalDateTime utcTime) {
        if (utcTime == null) return "";
        return utcTime.atZone(ZoneOffset.UTC)
            .withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
            .format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String translateStatus(AttendanceStatus status) {
        return switch (status) {
            case ON_TIME -> "Đúng giờ";
            case LATE_IN -> "Đi muộn";
            case EARLY_OUT -> "Về sớm";
            case LATE_IN_EARLY_OUT -> "Muộn & sớm";
            case HALF_DAY -> "Nửa ngày";
            case ABSENT -> "Vắng mặt";
            case INCOMPLETE -> "Thiếu checkout";
            default -> status.name();
        };
    }

    private String translateDayOfWeek(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "Thứ 2";
            case TUESDAY -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY -> "Thứ 5";
            case FRIDAY -> "Thứ 6";
            case SATURDAY -> "Thứ 7";
            case SUNDAY -> "Chủ nhật";
        };
    }
}
