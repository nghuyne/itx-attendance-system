package com.itx.attendance.service;

import com.itx.attendance.domain.IpScope;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.domain.ValidIp;
import com.itx.attendance.dto.request.CreateValidIpRequest;
import com.itx.attendance.dto.response.EmployeeDto;
import com.itx.attendance.dto.response.ValidIpDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.repository.ValidIpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidIpService {

    private final ValidIpRepository validIpRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<ValidIpDto> getAll(Pageable pageable) {
        return validIpRepository.findByActiveTrue(pageable).map(this::toDto);
    }

    @Transactional
    public ValidIpDto create(CreateValidIpRequest request, String createdByUsername) {
        String normalizedIp = request.ipAddress().trim();
        validateIpAddress(normalizedIp);

        User employee = null;
        if (request.scope() == IpScope.INDIVIDUAL) {
            if (request.employeeId() == null || request.employeeId().isBlank()) {
                throw new BusinessException(
                    "employeeId bắt buộc khi scope=INDIVIDUAL",
                    HttpStatus.BAD_REQUEST, "EMPLOYEE_ID_REQUIRED");
            }
            employee = userRepository.findById(request.employeeId())
                .orElseThrow(() -> new BusinessException(
                    "Nhân viên không tồn tại",
                    HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND"));
            if (employee.getRole() != UserRole.EMPLOYEE) {
                throw new BusinessException(
                    "User được chỉ định không phải EMPLOYEE",
                    HttpStatus.BAD_REQUEST, "NOT_AN_EMPLOYEE");
            }

            boolean duplicateIndividual = validIpRepository
                .existsByIpAddressAndScopeAndEmployeeIdAndActiveTrue(
                    normalizedIp, IpScope.INDIVIDUAL, request.employeeId());
            if (duplicateIndividual) {
                throw new BusinessException(
                    "IP này đã tồn tại cho nhân viên này",
                    HttpStatus.CONFLICT, "DUPLICATE_IP");
            }
        } else {
            if (request.employeeId() != null && !request.employeeId().isBlank()) {
                throw new BusinessException(
                    "employeeId không được cung cấp khi scope=COMPANY",
                    HttpStatus.BAD_REQUEST, "EMPLOYEE_ID_NOT_ALLOWED");
            }
            boolean duplicateCompany = validIpRepository
                .existsByIpAddressAndScopeAndEmployeeIsNullAndActiveTrue(
                    normalizedIp, IpScope.COMPANY);
            if (duplicateCompany) {
                throw new BusinessException(
                    "IP này đã tồn tại ở cấp công ty",
                    HttpStatus.CONFLICT, "DUPLICATE_IP");
            }
        }

        User admin = userRepository.findByUsername(createdByUsername)
            .orElseThrow(() -> new BusinessException(
                "Admin không tồn tại",
                HttpStatus.INTERNAL_SERVER_ERROR, "ADMIN_NOT_FOUND"));

        ValidIp validIp = ValidIp.builder()
            .ipAddress(normalizedIp)
            .scope(request.scope())
            .employee(employee)
            .description(request.description())
            .createdBy(admin)
            .build();

        try {
            ValidIp saved = validIpRepository.save(validIp);
            return toDto(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                "IP này đã tồn tại (duplicate)",
                HttpStatus.CONFLICT, "DUPLICATE_IP");
        }
    }

    @Transactional
    public void delete(Long id) {
        ValidIp validIp = validIpRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                "IP không tồn tại",
                HttpStatus.NOT_FOUND, "IP_NOT_FOUND"));
        validIpRepository.delete(validIp);
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> getEmployees() {
        return userRepository.findByRole(UserRole.EMPLOYEE).stream()
            .filter(User::isActive)
            .map(u -> EmployeeDto.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .username(u.getUsername())
                .build())
            .toList();
    }

    private void validateIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            throw new BusinessException(
                "IP address không được rỗng",
                HttpStatus.BAD_REQUEST, "INVALID_IP_FORMAT");
        }
        boolean isValidIpv4 = ip.matches(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)$");
        boolean looksLikeIpv6 = ip.contains(":") &&
            ip.matches("^[0-9a-fA-F:]+$");
        if (!isValidIpv4 && !looksLikeIpv6) {
            throw new BusinessException(
                "Định dạng IP không hợp lệ. Hỗ trợ IPv4 (203.0.113.45) và IPv6 (2001:db8::1)",
                HttpStatus.BAD_REQUEST, "INVALID_IP_FORMAT");
        }
    }

    private ValidIpDto toDto(ValidIp validIp) {
        return ValidIpDto.builder()
            .id(validIp.getId())
            .ipAddress(validIp.getIpAddress())
            .scope(validIp.getScope())
            .employeeId(validIp.getEmployee() != null ? validIp.getEmployee().getId() : null)
            .employeeName(validIp.getEmployee() != null ? validIp.getEmployee().getFullName() : null)
            .description(validIp.getDescription())
            .createdAt(validIp.getCreatedAt())
            .build();
    }
}
