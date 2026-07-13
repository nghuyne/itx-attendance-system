-- V2__users.sql
-- Story 1.2: User Entity & Database Schema Foundation

CREATE TABLE users (
    id            CHAR(36)     NOT NULL DEFAULT (UUID()),
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          ENUM('EMPLOYEE', 'LEADER', 'ADMIN') NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    leader_id     CHAR(36)     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT fk_users_leader
        FOREIGN KEY (leader_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
