CREATE TABLE revoked_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    revoked_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_revoked_token_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
