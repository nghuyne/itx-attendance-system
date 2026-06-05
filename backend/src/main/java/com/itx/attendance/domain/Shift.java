package com.itx.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, length = 36,
            columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "shift_start_time", nullable = false)
    private LocalTime shiftStartTime;

    @Column(name = "shift_end_time", nullable = false)
    private LocalTime shiftEndTime;

    @Column(name = "check_in_open_minutes", nullable = false)
    @Builder.Default
    private int checkInOpenMinutes = 30;

    @Column(name = "late_in_threshold", nullable = false)
    @Builder.Default
    private int lateInThreshold = 0;

    @Column(name = "early_out_threshold", nullable = false)
    @Builder.Default
    private int earlyOutThreshold = 0;

    @Column(name = "half_day_threshold", nullable = false)
    @Builder.Default
    private int halfDayThreshold = 30;

    @Column(name = "ot_buffer", nullable = false)
    @Builder.Default
    private int otBuffer = 30;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
