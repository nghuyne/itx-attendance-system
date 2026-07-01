-- Add EXCUSED to attendance_status ENUM column to match the Java AttendanceStatus enum
ALTER TABLE attendance_records
    MODIFY COLUMN attendance_status
        ENUM('ON_TIME','LATE_IN','EARLY_OUT','LATE_IN_EARLY_OUT','HALF_DAY','INCOMPLETE','ABSENT','EXCUSED')
        NOT NULL
        COMMENT 'State Machine status (provisional at check-in)';
