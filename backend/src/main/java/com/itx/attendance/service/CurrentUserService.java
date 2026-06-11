package com.itx.attendance.service;

import com.itx.attendance.domain.User;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        String username = SecurityUtil.getCurrentUsername();
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException(
                "User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
    }
}
