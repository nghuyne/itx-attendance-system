package com.itx.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "valid_macs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ValidMac {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "bssid", nullable = false, unique = true, length = 17)
    private String bssid;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
