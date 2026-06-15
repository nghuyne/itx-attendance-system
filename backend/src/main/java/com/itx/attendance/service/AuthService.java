package com.itx.attendance.service;

import com.itx.attendance.domain.RevokedToken;
import com.itx.attendance.domain.User;
import com.itx.attendance.dto.request.LoginRequest;
import com.itx.attendance.dto.response.AuthResponse;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.RevokedTokenRepository;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RevokedTokenRepository revokedTokenRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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
                        .mustChangePassword(user.isMustChangePassword())
                        .build())
                .build();

        return new LoginResult(response, refreshToken);
    }

    public String refresh(String refreshToken) {
        if (!jwtTokenProvider.isTokenValid(refreshToken)) {
            throw new BusinessException("Refresh token không hợp lệ hoặc đã hết hạn",
                    HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }
        String tokenHash = sha256(refreshToken);
        if (revokedTokenRepository.existsByTokenHash(tokenHash)) {
            throw new BusinessException("Refresh token đã bị thu hồi",
                    HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED");
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

    public void logout(String accessToken, String refreshToken) {
        revokeToken(accessToken);
        revokeToken(refreshToken);
    }

    private void revokeToken(String token) {
        if (token != null && !token.isBlank()) {
            String tokenHash = sha256(token);
            if (!revokedTokenRepository.existsByTokenHash(tokenHash)) {
                revokedTokenRepository.save(RevokedToken.builder().tokenHash(tokenHash).build());
            }
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, oldPassword)
            );
        } catch (AuthenticationException e) {
            throw new BusinessException(
                "Mật khẩu cũ không đúng",
                HttpStatus.BAD_REQUEST,
                "INVALID_OLD_PASSWORD"
            );
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    public record LoginResult(AuthResponse authResponse, String refreshToken) {}
}
