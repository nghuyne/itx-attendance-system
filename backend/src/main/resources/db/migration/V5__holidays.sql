CREATE TABLE holidays (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    date       DATE         NOT NULL COMMENT 'Gregorian date (ISO: YYYY-MM-DD)',
    name       VARCHAR(255) NOT NULL COMMENT 'Tên ngày lễ',
    type       ENUM('FIXED','DYNAMIC') NOT NULL
                            COMMENT 'FIXED: Dương lịch cố định; DYNAMIC: Âm lịch (lưu dạng DL)',
    year       INT          NOT NULL COMMENT 'Năm áp dụng',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_holiday_date (date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Ngày lễ cố định (Dương lịch) và linh hoạt (Âm lịch lưu dạng DL)';

-- Seed: Ngày lễ Dương lịch VN — năm 2026
INSERT INTO holidays (date, name, type, year) VALUES
    ('2026-01-01', 'Tết Dương Lịch',               'FIXED', 2026),
    ('2026-04-30', 'Ngày Giải Phóng Miền Nam',      'FIXED', 2026),
    ('2026-05-01', 'Ngày Quốc tế Lao Động',         'FIXED', 2026),
    ('2026-09-02', 'Quốc Khánh',                    'FIXED', 2026);

-- Seed: Ngày lễ Dương lịch VN — năm 2027
INSERT INTO holidays (date, name, type, year) VALUES
    ('2027-01-01', 'Tết Dương Lịch',               'FIXED', 2027),
    ('2027-04-30', 'Ngày Giải Phóng Miền Nam',      'FIXED', 2027),
    ('2027-05-01', 'Ngày Quốc tế Lao Động',         'FIXED', 2027),
    ('2027-09-02', 'Quốc Khánh',                    'FIXED', 2027);
