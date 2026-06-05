-- V4__valid_ips.sql
-- Story 2.2: Valid Public IP Management (ADR-002)

CREATE TABLE valid_ips (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    ip_address  VARCHAR(45)  NOT NULL COMMENT 'IPv4 hoặc IPv6 public IP văn phòng',
    scope       ENUM('COMPANY','INDIVIDUAL') NOT NULL DEFAULT 'COMPANY'
                             COMMENT 'COMPANY: tất cả nhân viên; INDIVIDUAL: nhân viên cụ thể',
    employee_id CHAR(36)     NULL     COMMENT 'FK → users (null khi scope=COMPANY)',
    description VARCHAR(255) NULL     COMMENT 'Ghi chú: vd Văn phòng HCM, IP dự phòng',
    created_by  CHAR(36)     NOT NULL COMMENT 'FK → users (admin tạo)',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    UNIQUE KEY uk_ip_scope (ip_address, scope, employee_id),
    KEY idx_ip_scope (scope),
    KEY idx_ip_employee (employee_id),
    KEY idx_ip_is_active (is_active),

    CONSTRAINT fk_valid_ips_employee FOREIGN KEY (employee_id) REFERENCES users(id),
    CONSTRAINT fk_valid_ips_created_by FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Whitelist Public IP hợp lệ cho Office Mode check-in';
