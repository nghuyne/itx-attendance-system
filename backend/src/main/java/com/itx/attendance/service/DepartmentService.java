package com.itx.attendance.service;

import com.itx.attendance.domain.AuditLog;
import com.itx.attendance.domain.Department;
import com.itx.attendance.domain.Shift;
import com.itx.attendance.domain.User;
import com.itx.attendance.domain.UserRole;
import com.itx.attendance.dto.request.AssignEmployeeDepartmentRequest;
import com.itx.attendance.dto.request.CreateDepartmentRequest;
import com.itx.attendance.dto.response.BulkAssignResultDto;
import com.itx.attendance.dto.response.DepartmentDto;
import com.itx.attendance.dto.response.EmployeeWithDeptDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.AuditLogRepository;
import com.itx.attendance.repository.DepartmentRepository;
import com.itx.attendance.repository.ShiftRepository;
import com.itx.attendance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final ShiftRepository shiftRepository;
    private final AuditLogRepository auditLogRepository;

    public List<DepartmentDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public DepartmentDto createDepartment(CreateDepartmentRequest request) {
        if (departmentRepository.existsByName(request.name().strip())) {
            throw new BusinessException("Phòng ban đã tồn tại", HttpStatus.CONFLICT, "DEPARTMENT_ALREADY_EXISTS");
        }
        Department dept = Department.builder()
            .name(request.name().strip())
            .description(request.description() != null ? request.description().strip() : null)
            .build();
        try {
            dept = departmentRepository.save(dept);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Phòng ban đã tồn tại", HttpStatus.CONFLICT, "DEPARTMENT_ALREADY_EXISTS");
        }
        return toDto(dept);
    }

    @Transactional
    public DepartmentDto updateDepartment(Long id, CreateDepartmentRequest request) {
        Department dept = findByIdOrThrow(id);
        if (!dept.getName().equals(request.name().strip()) &&
            departmentRepository.existsByName(request.name().strip())) {
            throw new BusinessException("Phòng ban đã tồn tại", HttpStatus.CONFLICT, "DEPARTMENT_ALREADY_EXISTS");
        }
        dept.setName(request.name().strip());
        dept.setDescription(request.description() != null ? request.description().strip() : null);
        try {
            dept = departmentRepository.save(dept);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Phòng ban đã tồn tại", HttpStatus.CONFLICT, "DEPARTMENT_ALREADY_EXISTS");
        }
        return toDto(dept);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        Department dept = findByIdOrThrow(id);
        long employeeCount = userRepository.countByDepartmentId(id);
        if (employeeCount > 0) {
            throw new BusinessException("Phòng ban còn nhân viên", HttpStatus.BAD_REQUEST, "DEPARTMENT_HAS_EMPLOYEES");
        }
        departmentRepository.delete(dept);
    }

    @Transactional
    public BulkAssignResultDto assignShiftToDepartment(Long deptId, String shiftId, User admin) {
        findByIdOrThrow(deptId);
        Shift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new BusinessException("Ca không tồn tại", HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND"));

        List<User> employees = userRepository.findByDepartmentId(deptId);
        if (employees.isEmpty()) {
            throw new BusinessException("Phòng ban không có nhân viên", HttpStatus.BAD_REQUEST, "DEPARTMENT_EMPTY");
        }

        int count = 0;
        for (User emp : employees) {
            String oldShiftName = emp.getShift() != null ? emp.getShift().getName() : null;
            emp.setShift(shift);
            userRepository.save(emp);
            auditLogRepository.save(new AuditLog(
                admin, "users", emp.getId(),
                "shift_id",
                oldShiftName,
                shift.getName(),
                "Department bulk shift assignment"
            ));
            count++;
        }
        return new BulkAssignResultDto(count);
    }

    public List<EmployeeWithDeptDto> getEmployeesWithDept() {
        return userRepository.findByRole(UserRole.EMPLOYEE).stream()
            .map(this::toEmployeeWithDeptDto)
            .toList();
    }

    @Transactional
    public EmployeeWithDeptDto assignEmployeeDepartment(String userId, AssignEmployeeDepartmentRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("Nhân viên không tồn tại", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        if (request.departmentId() == null) {
            user.setDepartment(null);
        } else {
            Department dept = findByIdOrThrow(request.departmentId());
            user.setDepartment(dept);
        }
        user = userRepository.save(user);
        return toEmployeeWithDeptDto(user);
    }

    private Department findByIdOrThrow(Long id) {
        return departmentRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Phòng ban không tồn tại", HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND"));
    }

    private DepartmentDto toDto(Department dept) {
        long count = userRepository.countByDepartmentId(dept.getId());
        return new DepartmentDto(dept.getId(), dept.getName(), dept.getDescription(), count);
    }

    private EmployeeWithDeptDto toEmployeeWithDeptDto(User user) {
        return new EmployeeWithDeptDto(
            user.getId(),
            user.getFullName(),
            user.getUsername(),
            user.getShift() != null ? user.getShift().getId() : null,
            user.getShift() != null ? user.getShift().getName() : null,
            user.getDepartment() != null ? user.getDepartment().getId() : null,
            user.getDepartment() != null ? user.getDepartment().getName() : null
        );
    }
}
