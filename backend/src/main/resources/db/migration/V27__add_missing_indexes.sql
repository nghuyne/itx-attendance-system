-- Deferred from code review of 5-1-admin-manual-override-attendance-records (2026-06-11):
-- findByFilters filters on admin_id, target_table, created_at but only created_at was indexed.
CREATE INDEX idx_audit_logs_admin_target_created ON audit_logs(admin_id, target_table, created_at);

-- Deferred from code review of 10-2-backend-valid-macs-checkin-hybrid (2026-06-29):
-- existsByBssidAndActiveTrue / findByActiveTrueOrderByCreatedAtDesc filter on is_active.
CREATE INDEX idx_valid_macs_active_bssid ON valid_macs(is_active, bssid);
