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

-- Seed: tài khoản admin mặc định
-- password_hash = BCrypt(rounds=10) của chuỗi 'admin123'
-- Generated and verified: BCryptPasswordEncoder(10).matches("admin123", hash) == true
INSERT INTO users (id, username, email, password_hash, full_name, role, is_active, leader_id)
VALUES (
    UUID(),
    'admin',
    'admin@itx.local',
    '$2a$10$XjCq.Z6n1spYh4K0Ac14HOnbegYb9HnaB1MbFCT7/qFic1/nUO7yK',
    'System Administrator',
    'ADMIN',
    TRUE,
    NULL
);
