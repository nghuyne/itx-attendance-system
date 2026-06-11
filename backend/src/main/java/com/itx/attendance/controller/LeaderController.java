package com.itx.attendance.controller;

import com.itx.attendance.domain.User;
import com.itx.attendance.dto.response.TeamRosterItemDto;
import com.itx.attendance.service.CurrentUserService;
import com.itx.attendance.service.LeaderService;
import com.itx.attendance.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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
    private final CurrentUserService currentUserService;

    @GetMapping("/team-roster")
    public ResponseEntity<List<TeamRosterItemDto>> getTeamRoster(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now(TimeUtil.UTC_PLUS_7);
        }
        User currentUser = currentUserService.getCurrentUser();
        return ResponseEntity.ok(leaderService.getTeamRoster(currentUser, date));
    }
}
