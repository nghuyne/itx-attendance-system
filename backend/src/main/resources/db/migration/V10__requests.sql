CREATE TABLE exception_requests (
    id                   CHAR(36)     NOT NULL COMMENT 'UUID',
    attendance_record_id CHAR(36)     NOT NULL COMMENT 'FK → attendance_records',
    employee_id          CHAR(36)     NOT NULL COMMENT 'FK → users',
    request_type         ENUM('LATE_IN','EARLY_OUT','HALF_DAY','LATE_IN_EARLY_OUT')
                         NOT NULL     COMMENT 'Type of exception request',
    reason               TEXT         NOT NULL COMMENT 'Reason for the exception request',
    status               ENUM('PENDING','APPROVED','REJECTED')
                         NOT NULL DEFAULT 'PENDING' COMMENT 'Request approval status',
    reviewed_by          CHAR(36)     NULL     COMMENT 'FK → users (reviewer)',
    review_reason        TEXT         NULL     COMMENT 'Reason for approval/rejection',
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_exception_pending (attendance_record_id, status) USING BTREE WHERE status='PENDING',
    KEY idx_requests_employee_status (employee_id, status),
    KEY idx_requests_attendance_status (attendance_record_id, status),

    CONSTRAINT fk_exception_attendance FOREIGN KEY (attendance_record_id) REFERENCES attendance_records(id),
    CONSTRAINT fk_exception_employee   FOREIGN KEY (employee_id)          REFERENCES users(id),
    CONSTRAINT fk_exception_reviewer   FOREIGN KEY (reviewed_by)          REFERENCES users(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Exception requests for attendance violations (LATE_IN, EARLY_OUT, etc.)';


CREATE TABLE adjustment_requests (
    id                      CHAR(36)     NOT NULL COMMENT 'UUID',
    attendance_record_id    CHAR(36)     NOT NULL COMMENT 'FK → attendance_records',
    employee_id             CHAR(36)     NOT NULL COMMENT 'FK → users',
    proposed_checkout_time  TIMESTAMP    NOT NULL COMMENT 'Proposed checkout time (UTC)',
    reason                  TEXT         NOT NULL COMMENT 'Reason for the adjustment request',
    status                  ENUM('PENDING','APPROVED','REJECTED')
                            NOT NULL DEFAULT 'PENDING' COMMENT 'Request approval status',
    reviewed_by             CHAR(36)     NULL     COMMENT 'FK → users (reviewer)',
    review_reason           TEXT         NULL     COMMENT 'Reason for approval/rejection',
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_adjustment_pending (attendance_record_id, status) USING BTREE WHERE status='PENDING',
    KEY idx_adjustment_employee_status (employee_id, status),
    KEY idx_adjustment_attendance_status (attendance_record_id, status),

    CONSTRAINT fk_adjustment_attendance FOREIGN KEY (attendance_record_id) REFERENCES attendance_records(id),
    CONSTRAINT fk_adjustment_employee   FOREIGN KEY (employee_id)          REFERENCES users(id),
    CONSTRAINT fk_adjustment_reviewer   FOREIGN KEY (reviewed_by)          REFERENCES users(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Adjustment requests for INCOMPLETE attendance records';
