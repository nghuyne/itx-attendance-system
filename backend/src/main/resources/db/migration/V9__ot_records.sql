CREATE TABLE ot_records (
    id                   CHAR(36)     NOT NULL COMMENT 'UUID',
    employee_id          CHAR(36)     NOT NULL COMMENT 'FK → users',
    attendance_record_id CHAR(36)     NOT NULL COMMENT 'FK → attendance_records',
    date                 DATE         NOT NULL,
    ot_duration_minutes  INT          NOT NULL,
    day_type             ENUM('WEEKDAY','WEEKEND','HOLIDAY') NOT NULL,
    ot_multiplier        DECIMAL(3,1) NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_ot_attendance (attendance_record_id),
    KEY idx_ot_employee_date (employee_id, date),

    CONSTRAINT fk_ot_employee   FOREIGN KEY (employee_id)          REFERENCES users(id),
    CONSTRAINT fk_ot_attendance FOREIGN KEY (attendance_record_id) REFERENCES attendance_records(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
