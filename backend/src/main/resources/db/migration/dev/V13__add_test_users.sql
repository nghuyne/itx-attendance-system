-- V13__add_test_users.sql
-- Add test users for development and testing

-- All test users use password 'admin123' (BCrypt hash)
-- password_hash = BCrypt(rounds=10) của chuỗi 'admin123'
-- Generated: $2a$10$XjCq.Z6n1spYh4K0Ac14HOnbegYb9HnaB1MbFCT7/qFic1/nUO7yK

-- Create LEADER user
INSERT INTO users (id, username, email, password_hash, full_name, role, is_active, leader_id)
VALUES (
    'leader-001',
    'leader1',
    'leader1@itx.local',
    '$2a$10$XjCq.Z6n1spYh4K0Ac14HOnbegYb9HnaB1MbFCT7/qFic1/nUO7yK',
    'Trần Văn A - Trưởng Phòng IT',
    'LEADER',
    TRUE,
    NULL
);

-- Create EMPLOYEE users under leader1
INSERT INTO users (id, username, email, password_hash, full_name, role, is_active, leader_id)
VALUES
(
    'emp-001',
    'employee1',
    'employee1@itx.local',
    '$2a$10$XjCq.Z6n1spYh4K0Ac14HOnbegYb9HnaB1MbFCT7/qFic1/nUO7yK',
    'Nguyễn Văn B',
    'EMPLOYEE',
    TRUE,
    'leader-001'
),
(
    'emp-002',
    'employee2',
    'employee2@itx.local',
    '$2a$10$XjCq.Z6n1spYh4K0Ac14HOnbegYb9HnaB1MbFCT7/qFic1/nUO7yK',
    'Phạm Thị C',
    'EMPLOYEE',
    TRUE,
    'leader-001'
),
(
    'emp-003',
    'employee3',
    'employee3@itx.local',
    '$2a$10$XjCq.Z6n1spYh4K0Ac14HOnbegYb9HnaB1MbFCT7/qFic1/nUO7yK',
    'Đỗ Minh D',
    'EMPLOYEE',
    TRUE,
    'leader-001'
);

-- Create another LEADER
INSERT INTO users (id, username, email, password_hash, full_name, role, is_active, leader_id)
VALUES (
    'leader-002',
    'leader2',
    'leader2@itx.local',
    '$2a$10$XjCq.Z6n1spYh4K0Ac14HOnbegYb9HnaB1MbFCT7/qFic1/nUO7yK',
    'Lê Thị E - Trưởng Phòng HR',
    'LEADER',
    TRUE,
    NULL
);

-- Create EMPLOYEE users under leader2
INSERT INTO users (id, username, email, password_hash, full_name, role, is_active, leader_id)
VALUES
(
    'emp-004',
    'employee4',
    'employee4@itx.local',
    '$2a$10$XjCq.Z6n1spYh4K0Ac14HOnbegYb9HnaB1MbFCT7/qFic1/nUO7yK',
    'Hoàng Văn F',
    'EMPLOYEE',
    TRUE,
    'leader-002'
),
(
    'emp-005',
    'employee5',
    'employee5@itx.local',
    '$2a$10$XjCq.Z6n1spYh4K0Ac14HOnbegYb9HnaB1MbFCT7/qFic1/nUO7yK',
    'Vũ Thanh G',
    'EMPLOYEE',
    TRUE,
    'leader-002'
);
