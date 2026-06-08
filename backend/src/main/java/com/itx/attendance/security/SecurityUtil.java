package com.itx.attendance.security;

import com.itx.attendance.domain.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public class SecurityUtil {

    private SecurityUtil() {}

    public static UserDetails getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return (UserDetails) auth.getPrincipal();
    }

    public static String getCurrentUsername() {
        return getCurrentUser().getUsername();
    }

    public static UserRole getCurrentRole() {
        return getCurrentUser().getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .map(UserRole::valueOf)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No role found for current user"));
    }

    public static boolean hasRole(String role) {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
