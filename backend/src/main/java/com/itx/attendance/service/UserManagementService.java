package com.itx.attendance.service;

import com.itx.attendance.domain.AuditLog;
import com.itx.attendance.domain.Department;
import com.itx.attendance.domain.Shift;
import com.itx.attendance.domain.User;
import com.itx.attendance.dto.request.CreateUserRequest;
import com.itx.attendance.dto.response.AdminUserDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.AuditLogRepository;
import com.itx.attendance.repository.DepartmentRepository;
import com.itx.attendance.repository.ShiftRepository;
import com.itx.attendance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final String TEMP_PASSWORD_CHARS =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ShiftRepository shiftRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public Page<AdminUserDto> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional
    public AdminUserDto createUser(CreateUserRequest request, User admin) {
        String username = request.username().strip();
        String email = request.email().strip().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("Tên đăng nhập đã tồn tại", HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email đã tồn tại", HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
        }

        Department department = request.departmentId() != null ? findDepartmentOrThrow(request.departmentId()) : null;
        Shift shift = request.shiftId() != null ? findShiftOrThrow(request.shiftId()) : null;

        String tempPassword = generateTempPassword();
        User user = User.builder()
            .username(username)
            .email(email)
            .fullName(request.fullName().strip())
            .role(request.role())
            .passwordHash(passwordEncoder.encode(tempPassword))
            .mustChangePassword(true)
            .active(true)
            .department(department)
            .shift(shift)
            .build();

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Tên đăng nhập hoặc email đã tồn tại", HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS");
        }

        auditLogRepository.save(new AuditLog(admin, "users", user.getId(),
            "create", null, username, "Admin tạo tài khoản mới"));

        sendCredentialsEmail(admin.getId(), user, tempPassword,
            "[ITX] Tài khoản của bạn đã được tạo",
            "Tài khoản ITX Attendance của bạn đã được tạo.");

        return toDto(user);
    }

    @Transactional
    public void resetPassword(String userId, User admin) {
        User user = findUserOrThrow(userId);
        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);

        auditLogRepository.save(new AuditLog(admin, "users", user.getId(),
            "password_hash", null, null, "Admin đặt lại mật khẩu"));

        sendCredentialsEmail(admin.getId(), user, tempPassword,
            "[ITX] Mật khẩu của bạn đã được đặt lại",
            "Quản trị viên đã đặt lại mật khẩu cho tài khoản của bạn.");
    }

    @Transactional
    public AdminUserDto setActive(String userId, boolean active, User admin) {
        User user = findUserOrThrow(userId);
        if (!active && user.getId().equals(admin.getId())) {
            throw new BusinessException("Không thể tự vô hiệu hóa tài khoản của chính mình",
                HttpStatus.BAD_REQUEST, "CANNOT_DEACTIVATE_SELF");
        }
        boolean previous = user.isActive();
        user.setActive(active);
        user = userRepository.save(user);

        auditLogRepository.save(new AuditLog(admin, "users", user.getId(),
            "is_active", String.valueOf(previous), String.valueOf(active),
            active ? "Admin kích hoạt tài khoản" : "Admin vô hiệu hóa tài khoản"));

        return toDto(user);
    }

    private void sendCredentialsEmail(String adminId, User user, String tempPassword, String subject, String intro) {
        String body = intro + "\n\n" +
            "Tên đăng nhập: " + user.getUsername() + "\n" +
            "Mật khẩu tạm thời: " + tempPassword + "\n\n" +
            "Vui lòng đăng nhập và đổi mật khẩu ngay khi có thể.";
        emailService.sendEmailAsync(adminId, user.getId(), user.getEmail(), subject, body);
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(secureRandom.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private User findUserOrThrow(String id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Người dùng không tồn tại", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
    }

    private Department findDepartmentOrThrow(Long id) {
        return departmentRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Phòng ban không tồn tại", HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND"));
    }

    private Shift findShiftOrThrow(String id) {
        return shiftRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Ca không tồn tại", HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND"));
    }

    private AdminUserDto toDto(User user) {
        return new AdminUserDto(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.isActive(),
            user.isMustChangePassword(),
            user.getDepartment() != null ? user.getDepartment().getId() : null,
            user.getDepartment() != null ? user.getDepartment().getName() : null,
            user.getShift() != null ? user.getShift().getId() : null,
            user.getShift() != null ? user.getShift().getName() : null,
            user.getCreatedAt()
        );
    }
}
