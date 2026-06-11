package com.itx.attendance.service;

import com.itx.attendance.domain.*;
import com.itx.attendance.dto.response.TeamRosterItemDto;
import com.itx.attendance.repository.AdjustmentRequestRepository;
import com.itx.attendance.repository.AttendanceRecordRepository;
import com.itx.attendance.repository.ExceptionRequestRepository;
import com.itx.attendance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LeaderService {

    private final UserRepository userRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ExceptionRequestRepository exceptionRequestRepository;
    private final AdjustmentRequestRepository adjustmentRequestRepository;

    @Transactional(readOnly = true)
    public List<TeamRosterItemDto> getTeamRoster(User leader, LocalDate date) {
        List<User> teamMembers;
        if (leader.getRole() == UserRole.ADMIN) {
            teamMembers = userRepository.findByRole(UserRole.EMPLOYEE);
        } else {
            teamMembers = userRepository.findByLeaderId(leader.getId());
        }
        if (teamMembers.isEmpty()) return List.of();

        List<String> employeeIds = teamMembers.stream().map(User::getId).toList();
        List<AttendanceRecord> records = attendanceRecordRepository.findByEmployeeIdInAndDate(employeeIds, date);
        Map<String, AttendanceRecord> recordByEmployeeId = records.stream()
            .collect(java.util.stream.Collectors.toMap(
                r -> r.getEmployee().getId(), r -> r, (existing, duplicate) -> existing));

        Map<String, String[]> pendingByRecordId = new HashMap<>();
        if (!records.isEmpty()) {
            List<String> recordIds = records.stream().map(AttendanceRecord::getId).toList();
            exceptionRequestRepository.findByAttendanceRecordIdInAndStatus(recordIds, RequestStatus.PENDING)
                .forEach(r -> pendingByRecordId.put(r.getAttendanceRecord().getId(),
                    new String[]{r.getId(), "EXCEPTION"}));
            adjustmentRequestRepository.findByAttendanceRecordIdInAndStatus(recordIds, RequestStatus.PENDING)
                .forEach(r -> pendingByRecordId.putIfAbsent(r.getAttendanceRecord().getId(),
                    new String[]{r.getId(), "ADJUSTMENT"}));
        }

        return teamMembers.stream()
            .map(employee -> buildRosterItem(employee, recordByEmployeeId.get(employee.getId()), pendingByRecordId))
            .sorted(Comparator.comparing(TeamRosterItemDto::employeeName))
            .toList();
    }

    private TeamRosterItemDto buildRosterItem(User employee, AttendanceRecord record,
                                              Map<String, String[]> pendingByRecordId) {
        if (record == null) {
            Shift shift = employee.getShift();
            return TeamRosterItemDto.builder()
                .employeeId(employee.getId())
                .employeeName(employee.getFullName())
                .shiftId(shift != null ? shift.getId() : null)
                .shiftName(shift != null ? shift.getName() : null)
                .shiftStartTime(shift != null ? shift.getShiftStartTime() : null)
                .shiftEndTime(shift != null ? shift.getShiftEndTime() : null)
                .attendanceStatus(null)
                .approvalSubStatus(null)
                .checkInTime(null)
                .checkOutTime(null)
                .hasPendingRequest(false)
                .pendingRequestId(null)
                .pendingRequestCategory(null)
                .build();
        }

        String[] pending = pendingByRecordId.get(record.getId());
        Shift shift = record.getShift();
        return TeamRosterItemDto.builder()
            .employeeId(employee.getId())
            .employeeName(employee.getFullName())
            .shiftId(shift != null ? shift.getId() : null)
            .shiftName(shift != null ? shift.getName() : null)
            .shiftStartTime(shift != null ? shift.getShiftStartTime() : null)
            .shiftEndTime(shift != null ? shift.getShiftEndTime() : null)
            .attendanceStatus(record.getAttendanceStatus())
            .approvalSubStatus(record.getApprovalSubStatus())
            .checkInTime(record.getCheckInTime())
            .checkOutTime(record.getCheckOutTime())
            .hasPendingRequest(pending != null)
            .pendingRequestId(pending != null ? pending[0] : null)
            .pendingRequestCategory(pending != null ? pending[1] : null)
            .build();
    }
}
