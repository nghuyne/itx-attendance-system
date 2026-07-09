package com.itx.attendance.security;

import com.itx.attendance.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_returnsPrincipal_whenAuthenticated() {
        UserDetails principal = User.builder()
                .username("alice")
                .password("hash")
                .roles("EMPLOYEE")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertThat(SecurityUtil.getCurrentUser().getUsername()).isEqualTo("alice");
        assertThat(SecurityUtil.getCurrentUsername()).isEqualTo("alice");
    }

    @Test
    void getCurrentUser_throws401BusinessException_whenNoAuthenticationInContext() {
        assertThatThrownBy(SecurityUtil::getCurrentUser)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void getCurrentUser_throws401BusinessException_whenPrincipalIsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(SecurityUtil::getCurrentUser)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
