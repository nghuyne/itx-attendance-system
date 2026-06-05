package com.itx.attendance.service;

import com.itx.attendance.domain.Shift;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.CreateShiftRequest;
import com.itx.attendance.dto.response.ShiftDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.ShiftRepository;
import com.itx.attendance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;

    public Page<ShiftDto> getAll(Pageable pageable) {
        return shiftRepository.findAll(pageable)
            .map(shift -> toDto(shift, userRepository.countByShiftId(shift.getId())));
    }

    @Transactional
    public ShiftDto create(CreateShiftRequest request) {
        validateTime(request.startTime(), request.endTime());
        if (shiftRepository.existsByName(request.name())) {
            throw new BusinessException("Ten ca '" + request.name() + "' da ton tai",
                HttpStatus.CONFLICT, "SHIFT_NAME_EXISTS");
        }
        Shift shift = Shift.builder()
            .name(request.name())
            .shiftStartTime(request.startTime())
            .shiftEndTime(request.endTime())
            .checkInOpenMinutes(request.checkInOpenMinutes())
            .lateInThreshold(request.lateInThreshold())
            .earlyOutThreshold(request.earlyOutThreshold())
            .halfDayThreshold(request.halfDayThreshold())
            .otBuffer(request.otBuffer())
            .build();
        Shift saved = shiftRepository.save(shift);
        return toDto(saved, 0L);
    }

    @Transactional
    public ShiftDto update(String id, CreateShiftRequest request) {
        Shift shift = shiftRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Ca khong ton tai",
                HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND"));
        validateTime(request.startTime(), request.endTime());
        if (shiftRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new BusinessException("Ten ca '" + request.name() + "' da ton tai",
                HttpStatus.CONFLICT, "SHIFT_NAME_EXISTS");
        }
        shift.setName(request.name());
        shift.setShiftStartTime(request.startTime());
        shift.setShiftEndTime(request.endTime());
        shift.setCheckInOpenMinutes(request.checkInOpenMinutes());
        shift.setLateInThreshold(request.lateInThreshold());
        shift.setEarlyOutThreshold(request.earlyOutThreshold());
        shift.setHalfDayThreshold(request.halfDayThreshold());
        shift.setOtBuffer(request.otBuffer());
        Shift saved = shiftRepository.save(shift);
        return toDto(saved, userRepository.countByShiftId(id));
    }

    @Transactional
    public void delete(String id) {
        if (!shiftRepository.existsById(id)) {
            throw new BusinessException("Ca khong ton tai",
                HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND");
        }
        long assignedCount = userRepository.countByShiftId(id);
        if (assignedCount > 0) {
            throw new BusinessException(
                "Ca dang duoc gan cho " + assignedCount + " nhan vien",
                HttpStatus.CONFLICT, "SHIFT_IN_USE");
        }
        shiftRepository.deleteById(id);
    }

    @Transactional
    public ShiftDto assignToEmployee(String shiftId, String employeeId) {
        Shift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new BusinessException("Ca khong ton tai",
                HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND"));
        User employee = userRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessException("Nhan vien khong ton tai",
                HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND"));
        if (employee.getRole() != UserRole.EMPLOYEE) {
            throw new BusinessException("Chi co the gan ca cho tai khoan nhan vien",
                HttpStatus.BAD_REQUEST, "NOT_AN_EMPLOYEE");
        }
        employee.setShift(shift);
        userRepository.save(employee);
        return toDto(shift, userRepository.countByShiftId(shiftId));
    }

    private void validateTime(LocalTime start, LocalTime end) {
        if (!start.isBefore(end)) {
            throw new BusinessException("Gio bat dau phai nho hon gio ket thuc",
                HttpStatus.BAD_REQUEST, "INVALID_SHIFT_TIME");
        }
    }

    private ShiftDto toDto(Shift shift, long assignedCount) {
        return ShiftDto.builder()
            .id(shift.getId())
            .name(shift.getName())
            .startTime(shift.getShiftStartTime())
            .endTime(shift.getShiftEndTime())
            .checkInOpenMinutes(shift.getCheckInOpenMinutes())
            .lateInThreshold(shift.getLateInThreshold())
            .earlyOutThreshold(shift.getEarlyOutThreshold())
            .halfDayThreshold(shift.getHalfDayThreshold())
            .otBuffer(shift.getOtBuffer())
            .assignedCount(assignedCount)
            .createdAt(shift.getCreatedAt())
            .updatedAt(shift.getUpdatedAt())
            .build();
    }
}
