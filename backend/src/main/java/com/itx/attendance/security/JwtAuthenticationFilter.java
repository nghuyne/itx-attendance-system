package com.itx.attendance.security;

import com.itx.attendance.domain.User;
import com.itx.attendance.repository.RevokedTokenRepository;
import com.itx.attendance.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final RevokedTokenRepository revokedTokenRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        if (revokedTokenRepository.existsByTokenHash(sha256(token))) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtTokenProvider.isTokenValid(token)) {
                String username = jwtTokenProvider.extractUsername(token);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    User user = userRepository.findByUsername(username).orElse(null);
                    if (user != null && !isIssuedBeforePasswordChange(token, user)) {
                        if (user.isMustChangePassword() && !request.getRequestURI().startsWith("/api/auth/")) {
                            respondPasswordChangeRequired(response);
                            return;
                        }
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (Exception e) {
            // Token malformed/expired: pass through, Spring Security handles 401 via EntryPoint
        }

        filterChain.doFilter(request, response);
    }

    private void respondPasswordChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"error\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Vui lòng đổi mật khẩu trước khi tiếp tục\"}");
    }

    private boolean isIssuedBeforePasswordChange(String token, User user) {
        if (user.getPasswordChangedAt() == null) {
            return false;
        }
        Instant issuedAt = jwtTokenProvider.extractIssuedAt(token);
        Instant changedAt = user.getPasswordChangedAt().atZone(ZoneId.systemDefault()).toInstant();
        return issuedAt.isBefore(changedAt);
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
}
