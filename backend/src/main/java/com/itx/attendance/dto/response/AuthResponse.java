package com.itx.attendance.dto.response;

import com.itx.attendance.domain.UserRole;
import lombok.Builder;

@Builder
public record AuthResponse(
    String accessToken,
    UserDto user
) {
    @Builder
    public record UserDto(
        String id,
        String username,
        String fullName,
        UserRole role,
        boolean mustChangePassword
    ) {}
}
