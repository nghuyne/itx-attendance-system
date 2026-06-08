CREATE INDEX idx_attendance_records_employee_checkin_time
    ON attendance_records (employee_id, check_in_time);
