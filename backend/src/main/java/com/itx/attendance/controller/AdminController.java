package com.itx.attendance.controller;

import com.itx.attendance.dto.request.CreateShiftRequest;
import com.itx.attendance.dto.request.CreateValidIpRequest;
import com.itx.attendance.dto.response.EmployeeDto;
import com.itx.attendance.dto.response.ShiftDto;
import com.itx.attendance.dto.response.ValidIpDto;
import com.itx.attendance.security.SecurityUtil;
import com.itx.attendance.service.ShiftService;
import com.itx.attendance.service.ValidIpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {

    private final ShiftService shiftService;
    private final ValidIpService validIpService;

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
        return ResponseEntity.ok().build();
    }

    // ── Employee listing (for INDIVIDUAL scope dropdown) ──────────────────────

    @GetMapping("/employees")
    public ResponseEntity<List<EmployeeDto>> getEmployees() {
        return ResponseEntity.ok(validIpService.getEmployees());
    }
}
