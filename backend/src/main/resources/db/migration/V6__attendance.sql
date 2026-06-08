CREATE TABLE attendance_records (
    id                  CHAR(36)     NOT NULL COMMENT 'UUID',
    employee_id         CHAR(36)     NOT NULL COMMENT 'FK → users',
    shift_id            CHAR(36)     NOT NULL COMMENT 'FK → shifts (snapshot tại check-in)',

    date                DATE         NOT NULL COMMENT 'Ngày làm việc (UTC+7)',

    check_in_time       TIMESTAMP    NULL     COMMENT 'Check-in UTC',
    check_in_ip         VARCHAR(45)  NULL     COMMENT 'Public IP tại check-in',
    check_in_lat        DECIMAL(9,6) NULL     COMMENT 'GPS lat',
    check_in_lng        DECIMAL(9,6) NULL     COMMENT 'GPS lng',
    check_in_photo_url  VARCHAR(500) NULL     COMMENT 'MinIO URL — populated by Story 3.2',

    check_out_time      TIMESTAMP    NULL     COMMENT 'Check-out UTC, nullable',
    check_out_ip        VARCHAR(45)  NULL,
    check_out_lat       DECIMAL(9,6) NULL,
    check_out_lng       DECIMAL(9,6) NULL,
    check_out_photo_url VARCHAR(500) NULL,

    attendance_status   ENUM('ON_TIME','LATE_IN','EARLY_OUT','LATE_IN_EARLY_OUT',
                             'HALF_DAY','INCOMPLETE','ABSENT')
                        NOT NULL     COMMENT 'State Machine status (provisional at check-in)',
    approval_sub_status ENUM('PENDING_APPROVAL','PENDING_ADJUSTMENT',
                             'APPROVED','REJECTED','ADMIN_OVERRIDE')
                        NULL DEFAULT NULL,

    is_client_site      BOOLEAN      NOT NULL DEFAULT FALSE,
    gps_unavailable     BOOLEAN      NOT NULL DEFAULT FALSE,
    suspicious_location BOOLEAN      NOT NULL DEFAULT FALSE,
    is_admin_override   BOOLEAN      NOT NULL DEFAULT FALSE,

    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_employee_date (employee_id, date),
    KEY idx_attendance_status (attendance_status),
    KEY idx_attendance_date (date),
    KEY idx_attendance_check_in_time (check_in_time),

    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES users(id),
    CONSTRAINT fk_attendance_shift    FOREIGN KEY (shift_id)    REFERENCES shifts(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Bản ghi chấm công — một bản ghi per nhân viên per ngày';
