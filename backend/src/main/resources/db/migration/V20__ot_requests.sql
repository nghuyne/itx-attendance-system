ALTER TABLE notifications
    MODIFY COLUMN type ENUM(
        'EXCEPTION_REQUEST','ADJUSTMENT_REQUEST',
        'REQUEST_APPROVED','REQUEST_REJECTED','INCOMPLETE_RECORD',
        'LEAVE_REQUEST','OT_REQUEST'
    ) NOT NULL;

CREATE TABLE ot_requests (
    id CHAR(36) NOT NULL,
    employee_id CHAR(36) NOT NULL,
    planned_date DATE NOT NULL,
    planned_ot_hours DECIMAL(4,2) NOT NULL,
    reason TEXT NOT NULL,
    status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    approver_id CHAR(36) NULL,
    rejection_reason TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ot_requests_employee FOREIGN KEY (employee_id) REFERENCES users(id),
    CONSTRAINT fk_ot_requests_approver FOREIGN KEY (approver_id) REFERENCES users(id),
    INDEX idx_ot_requests_employee_status (employee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE ot_records
    ADD COLUMN ot_request_id CHAR(36) NULL,
    ADD CONSTRAINT fk_ot_records_ot_request
        FOREIGN KEY (ot_request_id) REFERENCES ot_requests(id) ON DELETE SET NULL;
