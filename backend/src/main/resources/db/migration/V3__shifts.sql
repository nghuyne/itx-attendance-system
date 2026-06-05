-- V3__shifts.sql
-- Story 2.1: Fixed Shift Management

CREATE TABLE shifts (
    id                    CHAR(36)     NOT NULL DEFAULT (UUID()),
    name                  VARCHAR(100) NOT NULL,
    shift_start_time      TIME         NOT NULL COMMENT 'Gio bat dau ca (HH:mm:ss, UTC+7)',
    shift_end_time        TIME         NOT NULL COMMENT 'Gio ket thuc ca (HH:mm:ss, UTC+7)',
    check_in_open_minutes INT          NOT NULL DEFAULT 30 COMMENT 'Mo cong check-in truoc N phut',
    late_in_threshold     INT          NOT NULL DEFAULT 0  COMMENT 'Nguong tre (phut)',
    early_out_threshold   INT          NOT NULL DEFAULT 0  COMMENT 'Nguong ve som (phut)',
    half_day_threshold    INT          NOT NULL DEFAULT 30 COMMENT 'Nguong nua ngay (phut)',
    ot_buffer             INT          NOT NULL DEFAULT 30 COMMENT 'Buffer truoc khi tinh OT (phut)',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_shifts_name (name),
    CONSTRAINT chk_shifts_time CHECK (shift_start_time < shift_end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Them shift_id FK vao users (nullable — nhan vien co the chua duoc gan ca)
ALTER TABLE users
    ADD COLUMN shift_id CHAR(36) NULL AFTER leader_id,
    ADD CONSTRAINT fk_users_shift
        FOREIGN KEY (shift_id) REFERENCES shifts(id)
        ON DELETE SET NULL;
