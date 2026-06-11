package com.itx.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false, updatable = false)
    private User admin;

    @Column(name = "target_table", nullable = false, updatable = false, length = 100)
    private String targetTable;

    @Column(name = "target_id", nullable = false, updatable = false, length = 36)
    private String targetId;

    @Column(name = "field_changed", nullable = false, updatable = false, length = 100)
    private String fieldChanged;

    @Column(name = "old_value", columnDefinition = "TEXT", updatable = false)
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT", updatable = false)
    private String newValue;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AuditLog(User admin, String targetTable, String targetId,
                    String fieldChanged, String oldValue, String newValue, String reason) {
        this.admin = admin;
        this.targetTable = targetTable;
        this.targetId = targetId;
        this.fieldChanged = fieldChanged;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
    }
}
