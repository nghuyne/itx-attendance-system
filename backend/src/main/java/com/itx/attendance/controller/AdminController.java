package com.itx.attendance.controller;

import com.itx.attendance.domain.AttendanceStatus;
import com.itx.attendance.dto.request.AssignDepartmentShiftRequest;
import com.itx.attendance.dto.request.AssignEmployeeDepartmentRequest;
import com.itx.attendance.dto.request.AttendanceOverrideRequest;
import com.itx.attendance.dto.request.CreateDepartmentRequest;
import com.itx.attendance.dto.request.CreateHolidayRequest;
import com.itx.attendance.dto.request.CreateOfficeLocationRequest;
import com.itx.attendance.dto.request.CreateShiftRequest;
import com.itx.attendance.dto.request.CreateValidIpRequest;
import com.itx.attendance.dto.response.AdminAttendanceRecordDto;
import com.itx.attendance.dto.response.AttendanceRecordDto;
import com.itx.attendance.dto.response.AuditLogDto;
import com.itx.attendance.dto.response.BulkAssignResultDto;
import com.itx.attendance.dto.response.DepartmentDto;
import com.itx.attendance.dto.response.EmployeeDto;
import com.itx.attendance.dto.response.EmployeeWithDeptDto;
import com.itx.attendance.dto.response.HolidayDto;
import com.itx.attendance.dto.response.OfficeLocationDto;
import com.itx.attendance.dto.response.ShiftDto;
import com.itx.attendance.dto.response.ValidIpDto;
import com.itx.attendance.security.SecurityUtil;
import com.itx.attendance.service.AdminOverrideService;
import com.itx.attendance.service.AttendanceExportService;
import com.itx.attendance.service.AuditLogService;
import com.itx.attendance.service.CurrentUserService;
import com.itx.attendance.service.DepartmentService;
import com.itx.attendance.service.HolidayService;
import com.itx.attendance.service.OfficeLocationService;
import com.itx.attendance.service.ShiftService;
import com.itx.attendance.service.ValidIpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {

    private final ShiftService shiftService;
    private final ValidIpService validIpService;
    private final HolidayService holidayService;
    private final AdminOverrideService adminOverrideService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final OfficeLocationService officeLocationService;
    private final AttendanceExportService attendanceExportService;
    private final DepartmentService departmentService;

    // ── Shift endpoints ───────────────────────────────────────────────────────

    @GetMapping("/shifts")
    public ResponseEntity<Page<ShiftDto>> getShifts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(shiftService.getAll(PageRequest.of(page, size)));
    }

    @PostMapping("/shifts")
    public ResponseEntity<ShiftDto> createShift(@Valid @RequestBody CreateShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.create(request));
    }

    @PutMapping("/shifts/{id}")
    public ResponseEntity<ShiftDto> updateShift(
            @PathVariable String id,
            @Valid @RequestBody CreateShiftRequest request) {
        return ResponseEntity.ok(shiftService.update(id, request));
    }

    @DeleteMapping("/shifts/{id}")
    public ResponseEntity<Void> deleteShift(@PathVariable String id) {
        shiftService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/shifts/{shiftId}/assign/{employeeId}")
    public ResponseEntity<ShiftDto> assignShift(
            @PathVariable String shiftId,
            @PathVariable String employeeId) {
        return ResponseEntity.ok(shiftService.assignToEmployee(shiftId, employeeId));
    }

    // ── Valid IP endpoints (Story 2.2) ────────────────────────────────────────

    @GetMapping("/valid-ips")
    public ResponseEntity<Page<ValidIpDto>> getValidIps(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(validIpService.getAll(PageRequest.of(page, size)));
    }

    @PostMapping("/valid-ips")
    public ResponseEntity<ValidIpDto> createValidIp(
            @Valid @RequestBody CreateValidIpRequest request) {
        String currentUsername = SecurityUtil.getCurrentUsername();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(validIpService.create(request, currentUsername));
    }

    @DeleteMapping("/valid-ips/{id}")
    public ResponseEntity<Void> deleteValidIp(@PathVariable Long id) {
        validIpService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Employee listing (for INDIVIDUAL scope dropdown) ──────────────────────

    @GetMapping("/employees")
    public ResponseEntity<List<EmployeeDto>> getEmployees() {
        return ResponseEntity.ok(validIpService.getEmployees());
    }

    // ── Holiday endpoints (Story 2.3) ─────────────────────────────────────────

    @GetMapping("/holidays")
    public ResponseEntity<Page<HolidayDto>> getHolidays(
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(holidayService.getAll(year, PageRequest.of(page, size)));
    }

    @PostMapping("/holidays")
    public ResponseEntity<HolidayDto> createHoliday(@Valid @RequestBody CreateHolidayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(holidayService.create(request));
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Long id) {
        holidayService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Attendance override endpoints (Story 5.1) ─────────────────────────────

    @PutMapping("/attendance/{id}/override")
    public ResponseEntity<AttendanceRecordDto> overrideAttendance(
            @PathVariable String id,
            @Valid @RequestBody AttendanceOverrideRequest request) {
        return ResponseEntity.ok(
            adminOverrideService.overrideAttendance(id, request, currentUserService.getCurrentUser()));
    }

    @GetMapping("/attendance")
    public ResponseEntity<Page<AdminAttendanceRecordDto>> getAttendance(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) List<AttendanceStatus> status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(
            adminOverrideService.searchAttendance(from, to, employeeId, status, PageRequest.of(page, size)));
    }

    @GetMapping("/attendance/export")
    public ResponseEntity<byte[]> exportAttendance(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String employeeId) {
        byte[] bytes = attendanceExportService.exportToExcel(from, to, employeeId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("attendance-" + from + "-" + to + ".xlsx")
            .build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ── Audit log endpoints (Story 5.2) ──────────────────────────────────────

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogDto>> getAuditLogs(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String adminId,
            @RequestParam(required = false) String targetTable,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(
            auditLogService.getAuditLogs(adminId, targetTable, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/admins")
    public ResponseEntity<List<EmployeeDto>> getAdmins() {
        return ResponseEntity.ok(auditLogService.getAdminUsers());
    }

    // ── Office location endpoints (Story 7.1) ────────────────────────────────

    @PostMapping("/office-locations")
    public ResponseEntity<OfficeLocationDto> createOfficeLocation(
            @Valid @RequestBody CreateOfficeLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(officeLocationService.create(request));
    }

    @GetMapping("/office-locations")
    public ResponseEntity<List<OfficeLocationDto>> getOfficeLocations() {
        return ResponseEntity.ok(officeLocationService.findAll());
    }

    @PutMapping("/office-locations/{id}")
    public ResponseEntity<OfficeLocationDto> updateOfficeLocation(
            @PathVariable Long id,
            @Valid @RequestBody CreateOfficeLocationRequest request) {
        return ResponseEntity.ok(officeLocationService.update(id, request));
    }

    @DeleteMapping("/office-locations/{id}")
    public ResponseEntity<Void> deleteOfficeLocation(@PathVariable Long id) {
        officeLocationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Department endpoints (Story 9.1) ─────────────────────────────────────

    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentDto>> getDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentDto> createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentService.createDepartment(request));
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<DepartmentDto> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, request));
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/departments/{id}/shift")
    public ResponseEntity<BulkAssignResultDto> assignShiftToDepartment(
            @PathVariable Long id,
            @Valid @RequestBody AssignDepartmentShiftRequest request) {
        return ResponseEntity.ok(
            departmentService.assignShiftToDepartment(id, request.shiftId(), currentUserService.getCurrentUser()));
    }

    // ── Employee with department endpoints (Story 9.1) ────────────────────────

    @GetMapping("/employees/details")
    public ResponseEntity<List<EmployeeWithDeptDto>> getEmployeesWithDept() {
        return ResponseEntity.ok(departmentService.getEmployeesWithDept());
    }

    @PutMapping("/employees/{userId}/department")
    public ResponseEntity<EmployeeWithDeptDto> assignEmployeeDepartment(
            @PathVariable String userId,
            @Valid @RequestBody AssignEmployeeDepartmentRequest request) {
        return ResponseEntity.ok(departmentService.assignEmployeeDepartment(userId, request));
    }
}
