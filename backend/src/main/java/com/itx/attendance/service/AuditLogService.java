package com.itx.attendance.service;

import com.itx.attendance.domain.AuditLog;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.response.AuditLogDto;
import com.itx.attendance.dto.response.EmployeeDto;
import com.itx.attendance.repository.AuditLogRepository;
import com.itx.attendance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public Page<AuditLogDto> getAuditLogs(
            String adminId,
            String targetTable,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {

        LocalDateTime fromDate = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDate = to != null ? to.atTime(23, 59, 59) : null;

        return auditLogRepository
            .findByFilters(adminId, targetTable, fromDate, toDate, pageable)
            .map(this::toDto);
    }

    public List<EmployeeDto> getAdminUsers() {
        return userRepository.findByRole(UserRole.ADMIN).stream()
            .map(u -> EmployeeDto.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .username(u.getUsername())
                .build())
            .toList();
    }

    private AuditLogDto toDto(AuditLog al) {
        return AuditLogDto.builder()
            .id(al.getId())
            .adminId(al.getAdmin().getId())
            .adminName(al.getAdmin().getFullName())
            .targetTable(al.getTargetTable())
            .targetId(al.getTargetId())
            .fieldChanged(al.getFieldChanged())
            .oldValue(al.getOldValue())
            .newValue(al.getNewValue())
            .reason(al.getReason())
            .createdAt(al.getCreatedAt())
            .build();
    }
}
