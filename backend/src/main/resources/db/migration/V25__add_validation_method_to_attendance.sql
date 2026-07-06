-- Records which network path (IP or BSSID) validated check-in, so check-out
-- can re-validate against the same path instead of always falling back to IP.
ALTER TABLE attendance_records
    ADD COLUMN validation_method ENUM('IP','BSSID','NONE')
        NOT NULL DEFAULT 'IP'
        COMMENT 'Network validation path used at check-in: IP, BSSID, or NONE (client-site)'
        AFTER is_client_site;
