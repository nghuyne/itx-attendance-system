package com.itx.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ot_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, length = 36,
            columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_record_id", nullable = false)
    private AttendanceRecord attendanceRecord;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "ot_duration_minutes", nullable = false)
    private int otDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false,
            columnDefinition = "ENUM('WEEKDAY','WEEKEND','HOLIDAY')")
    private DayType dayType;

    @Column(name = "ot_multiplier", nullable = false, precision = 3, scale = 1)
    private BigDecimal otMultiplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ot_request_id")
    private OtRequest otRequest;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
