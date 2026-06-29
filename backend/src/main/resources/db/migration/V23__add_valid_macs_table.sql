CREATE TABLE valid_macs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    bssid       VARCHAR(17)  NOT NULL COMMENT 'Format: XX:XX:XX:XX:XX:XX',
    description VARCHAR(255),
    created_by  VARCHAR(36)  NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_valid_macs_bssid (bssid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
