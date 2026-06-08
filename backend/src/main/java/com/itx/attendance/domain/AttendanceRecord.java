package com.itx.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, length = 36,
            columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_in_ip", length = 45)
    private String checkInIp;

    @Column(name = "check_in_lat", precision = 9, scale = 6)
    private BigDecimal checkInLat;

    @Column(name = "check_in_lng", precision = 9, scale = 6)
    private BigDecimal checkInLng;

    @Column(name = "check_in_photo_url", length = 500)
    private String checkInPhotoUrl;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "check_out_ip", length = 45)
    private String checkOutIp;

    @Column(name = "check_out_lat", precision = 9, scale = 6)
    private BigDecimal checkOutLat;

    @Column(name = "check_out_lng", precision = 9, scale = 6)
    private BigDecimal checkOutLng;

    @Column(name = "check_out_photo_url", length = 500)
    private String checkOutPhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false,
            columnDefinition = "ENUM('ON_TIME','LATE_IN','EARLY_OUT','LATE_IN_EARLY_OUT','HALF_DAY','INCOMPLETE','ABSENT')")
    private AttendanceStatus attendanceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_sub_status",
            columnDefinition = "ENUM('PENDING_APPROVAL','PENDING_ADJUSTMENT','APPROVED','REJECTED','ADMIN_OVERRIDE')")
    private ApprovalSubStatus approvalSubStatus;

    @Column(name = "is_client_site", nullable = false)
    @Builder.Default
    private boolean clientSite = false;

    @Column(name = "gps_unavailable", nullable = false)
    @Builder.Default
    private boolean gpsUnavailable = false;

    @Column(name = "suspicious_location", nullable = false)
    @Builder.Default
    private boolean suspiciousLocation = false;

    @Column(name = "is_admin_override", nullable = false)
    @Builder.Default
    private boolean adminOverride = false;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
