package com.itx.attendance.controller;

import com.itx.attendance.dto.request.CreateShiftRequest;
import com.itx.attendance.dto.response.ShiftDto;
import com.itx.attendance.service.ShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ShiftService shiftService;

    @GetMapping("/shifts")
    public ResponseEntity<Page<ShiftDto>> getShifts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
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
}
