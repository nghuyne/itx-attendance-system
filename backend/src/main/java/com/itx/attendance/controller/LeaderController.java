package com.itx.attendance.controller;

import com.itx.attendance.domain.User;
import com.itx.attendance.dto.response.TeamRosterItemDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.UserRepository;
import com.itx.attendance.security.SecurityUtil;
import com.itx.attendance.service.LeaderService;
import com.itx.attendance.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/leader")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("hasAnyRole('LEADER', 'ADMIN')")
public class LeaderController {

    private final LeaderService leaderService;
    private final UserRepository userRepository;

    @GetMapping("/team-roster")
    public ResponseEntity<List<TeamRosterItemDto>> getTeamRoster(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now(TimeUtil.UTC_PLUS_7);
        }
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(leaderService.getTeamRoster(currentUser, date));
    }

    private User getCurrentUser() {
        String username = SecurityUtil.getCurrentUsername();
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
    }
}
