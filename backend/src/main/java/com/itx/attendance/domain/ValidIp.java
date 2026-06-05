package com.itx.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "valid_ips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false,
            columnDefinition = "ENUM('COMPANY','INDIVIDUAL')")
    @Builder.Default
    private IpScope scope = IpScope.COMPANY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = true)
    private User employee;

    @Column(name = "description", length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
