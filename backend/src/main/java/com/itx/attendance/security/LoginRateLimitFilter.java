package com.itx.attendance.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    // Configurable so integration tests that log in many times per Spring context
    // (once per @Test via @BeforeEach, sharing this bean's bucket) can raise their
    // own budget without weakening the production default of 5/min/IP.
    @Value("${app.rate-limit.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.rate-limit.login.window-seconds:60}")
    private long windowSeconds;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // NOT getServletPath(): DispatcherServlet is mapped to the default "/" pattern
        // (no server.servlet.context-path configured), and per the Servlet spec a
        // default-mapped servlet always reports an empty getServletPath() — so that
        // comparison never matched anything and this filter silently rate-limited
        // nothing since it was introduced. getRequestURI() carries the real path.
        return !(HttpMethod.POST.matches(request.getMethod())
                && LOGIN_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = extractClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(maxAttempts)
                        .refillIntervally(maxAttempts, Duration.ofSeconds(windowSeconds))
                        .build())
                .build());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("error", "TOO_MANY_REQUESTS",
                       "message", "Quá nhiều lần thử đăng nhập. Vui lòng thử lại sau 1 phút.")
            ));
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        // request.getRemoteAddr() is already the trust-checked client IP: Tomcat's
        // RemoteIpValve (server.tomcat.remoteip.internal-proxies) rewrites it from
        // X-Real-IP/X-Forwarded-For only when the direct TCP peer is a configured
        // trusted proxy, and leaves it untouched otherwise. Reading the raw headers
        // here instead would let anyone who can reach this port directly spoof their
        // IP regardless of the trusted-proxy configuration.
        String ip = request.getRemoteAddr();

        // Chuẩn hóa IPv6 loopback — cùng normalize như AttendanceService.extractClientIp(),
        // tránh cùng 1 client bị tách thành 2 bucket rate-limit khác nhau.
        if (ip != null && ip.startsWith("::ffff:")) {
            ip = ip.substring(7);
        }
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }

        return ip;
    }
}
