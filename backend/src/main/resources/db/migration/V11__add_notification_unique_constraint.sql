ALTER TABLE notifications
    ADD CONSTRAINT uk_notifications_recipient_type_ref
    UNIQUE (recipient_id, type, reference_id);
