CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id CHAR(36) NOT NULL,
    target_table VARCHAR(100) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    field_changed VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    reason TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_admin FOREIGN KEY (admin_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
