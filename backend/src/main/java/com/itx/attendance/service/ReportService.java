package com.itx.attendance.service;

import com.itx.attendance.domain.AttendanceRecord;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserRepository userRepository;

    public byte[] exportAttendanceToExcel(LocalDate startDate, LocalDate endDate, User currentUser) {
        log.info("Generating Excel report from {} to {} requested by {}", startDate, endDate, currentUser.getUsername());

        List<AttendanceRecord> records;
        
        if (currentUser.getRole() == UserRole.ADMIN) {
            records = attendanceRecordRepository.findByDateBetweenOrderByDateDesc(startDate, endDate);
        } else if (currentUser.getRole() == UserRole.LEADER) {
            List<String> teamMemberIds = userRepository.findByLeaderId(currentUser.getId())
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());
            teamMemberIds.add(currentUser.getId()); // Include leader's own records
            records = attendanceRecordRepository.findByEmployeeIdInAndDateBetweenOrderByDateDesc(teamMemberIds, startDate, endDate);
        } else {
            records = attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateDesc(currentUser.getId(), startDate, endDate);
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Attendance Report");

            // Define header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] columns = {"Date", "Employee ID", "Employee Name", "Shift", "Check In", "Check Out", "Status", "Client Site"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            // Fill data
            int rowIdx = 1;
            for (AttendanceRecord record : records) {
                Row row = sheet.createRow(rowIdx++);
                
                row.createCell(0).setCellValue(record.getDate().format(dateFormatter));
                row.createCell(1).setCellValue(record.getEmployee().getUsername());
                row.createCell(2).setCellValue(record.getEmployee().getFullName());
                row.createCell(3).setCellValue(record.getShift().getName());
                row.createCell(4).setCellValue(record.getCheckInTime() != null ? record.getCheckInTime().format(timeFormatter) : "");
                row.createCell(5).setCellValue(record.getCheckOutTime() != null ? record.getCheckOutTime().format(timeFormatter) : "");
                row.createCell(6).setCellValue(record.getAttendanceStatus().name());
                row.createCell(7).setCellValue(record.isClientSite() ? "Yes" : "No");
            }

            // Auto-size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate Excel report", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }
}
