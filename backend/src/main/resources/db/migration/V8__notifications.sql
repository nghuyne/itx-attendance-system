CREATE TABLE notifications (
    id           CHAR(36)    NOT NULL COMMENT 'UUID',
    recipient_id CHAR(36)    NOT NULL COMMENT 'FK → users',
    type         ENUM('EXCEPTION_REQUEST','ADJUSTMENT_REQUEST',
                      'REQUEST_APPROVED','REQUEST_REJECTED','INCOMPLETE_RECORD')
                 NOT NULL,
    reference_id CHAR(36)    NULL     COMMENT 'FK reference to related record (attendance_records.id, etc.)',
    message      TEXT        NOT NULL,
    is_read      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_notifications_recipient_is_read (recipient_id, is_read),

    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='In-app notifications — được polling bởi frontend mỗi 15s (Epic 4)';
