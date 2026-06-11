-- V13__add_test_users.sql
-- Add test users for development and testing

-- Generate test users with password 'test123' (BCrypt hash)
-- password_hash = BCrypt(rounds=10) của chuỗi 'test123'
-- Generated: $2a$10$8P5/Q7.E.R5nJ9pYQ5R5He0PJpVpvTOv3Qr4pGzR7W4mK3A9L1tl2

-- Create LEADER user
INSERT INTO users (id, username, email, password_hash, full_name, role, is_active, leader_id)
VALUES (
    'leader-001',
    'leader1',
    'leader1@itx.local',
    '$2a$10$8P5/Q7.E.R5nJ9pYQ5R5He0PJpVpvTOv3Qr4pGzR7W4mK3A9L1tl2',
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
    '$2a$10$8P5/Q7.E.R5nJ9pYQ5R5He0PJpVpvTOv3Qr4pGzR7W4mK3A9L1tl2',
    'Nguyễn Văn B',
    'EMPLOYEE',
    TRUE,
    'leader-001'
),
(
    'emp-002',
    'employee2',
    'employee2@itx.local',
    '$2a$10$8P5/Q7.E.R5nJ9pYQ5R5He0PJpVpvTOv3Qr4pGzR7W4mK3A9L1tl2',
    'Phạm Thị C',
    'EMPLOYEE',
    TRUE,
    'leader-001'
),
(
    'emp-003',
    'employee3',
    'employee3@itx.local',
    '$2a$10$8P5/Q7.E.R5nJ9pYQ5R5He0PJpVpvTOv3Qr4pGzR7W4mK3A9L1tl2',
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
    '$2a$10$8P5/Q7.E.R5nJ9pYQ5R5He0PJpVpvTOv3Qr4pGzR7W4mK3A9L1tl2',
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
    '$2a$10$8P5/Q7.E.R5nJ9pYQ5R5He0PJpVpvTOv3Qr4pGzR7W4mK3A9L1tl2',
    'Hoàng Văn F',
    'EMPLOYEE',
    TRUE,
    'leader-002'
),
(
    'emp-005',
    'employee5',
    'employee5@itx.local',
    '$2a$10$8P5/Q7.E.R5nJ9pYQ5R5He0PJpVpvTOv3Qr4pGzR7W4mK3A9L1tl2',
    'Vũ Thanh G',
    'EMPLOYEE',
    TRUE,
    'leader-002'
);
