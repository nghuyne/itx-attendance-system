package com.itx.attendance.service;

import com.itx.attendance.domain.User;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.dto.response.AuthResponse;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResult login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (AuthenticationException e) {
            throw new BusinessException(
                "Tên đăng nhập hoặc mật khẩu không đúng",
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS"
            );
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .role(user.getRole())
                        .build())
                .build();

        return new LoginResult(response, refreshToken);
    }

    public String refresh(String refreshToken) {
        if (!jwtTokenProvider.isTokenValid(refreshToken)) {
            throw new BusinessException("Refresh token không hợp lệ hoặc đã hết hạn",
                    HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }
        String username = jwtTokenProvider.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found",
                        HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));
        if (!user.isActive()) {
            throw new BusinessException("Tài khoản đã bị vô hiệu hóa",
                    HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
        }
        return jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole());
    }

    public void logout() {}

    public record LoginResult(AuthResponse authResponse, String refreshToken) {}
}
