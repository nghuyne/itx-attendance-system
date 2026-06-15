ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Force change password for users seeded with admin123
UPDATE users SET must_change_password = TRUE WHERE username = 'admin' OR username LIKE 'leader%' OR username LIKE 'emp%';
