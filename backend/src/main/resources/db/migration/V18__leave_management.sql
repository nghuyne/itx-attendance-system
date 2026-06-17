CREATE TABLE leave_requests (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    employee_id     VARCHAR(36)     NOT NULL,
    leave_type      ENUM('ANNUAL','SICK') NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    total_days      INT             NOT NULL,
    reason          TEXT            NOT NULL,
    status          ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    approver_id     VARCHAR(36)     NULL,
    rejection_reason TEXT           NULL,
    created_at      DATETIME        NULL,
    updated_at      DATETIME        NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lr_employee FOREIGN KEY (employee_id) REFERENCES users(id),
    CONSTRAINT fk_lr_approver FOREIGN KEY (approver_id) REFERENCES users(id)
);

CREATE TABLE leave_balances (
    id          BIGINT  NOT NULL AUTO_INCREMENT,
    employee_id VARCHAR(36) NOT NULL,
    year        INT     NOT NULL,
    leave_type  ENUM('ANNUAL','SICK') NOT NULL,
    total_days  INT     NOT NULL,
    used_days   INT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_leave_balance (employee_id, year, leave_type),
    CONSTRAINT fk_lb_employee FOREIGN KEY (employee_id) REFERENCES users(id)
);

CREATE INDEX idx_leave_requests_employee_status ON leave_requests (employee_id, status);

INSERT INTO leave_balances (employee_id, year, leave_type, total_days, used_days)
SELECT id, YEAR(CURDATE()), 'ANNUAL', 12, 0
FROM users
WHERE is_active = 1 AND role = 'EMPLOYEE'
ON DUPLICATE KEY UPDATE total_days = total_days;

INSERT INTO leave_balances (employee_id, year, leave_type, total_days, used_days)
SELECT id, YEAR(CURDATE()), 'SICK', 5, 0
FROM users
WHERE is_active = 1 AND role = 'EMPLOYEE'
ON DUPLICATE KEY UPDATE total_days = total_days;
