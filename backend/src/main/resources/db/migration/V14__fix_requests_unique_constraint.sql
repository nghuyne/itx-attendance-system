-- V14__fix_requests_unique_constraint.sql
-- Drop unique constraints that incorrectly block re-submission after rejection.
-- The backend service already enforces the PENDING-only check at the application layer,
-- so these DB-level constraints are redundant and harmful.

ALTER TABLE exception_requests DROP INDEX uk_exception_pending;
ALTER TABLE adjustment_requests DROP INDEX uk_adjustment_pending;
