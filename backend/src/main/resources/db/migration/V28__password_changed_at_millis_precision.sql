-- password_changed_at was DATETIME (whole-second precision). MySQL rounds a
-- LocalDateTime.now() write (millisecond precision) to the nearest second on
-- insert, which can round the stored value forward past the issuedAt of a JWT
-- minted moments later by the same request flow (change-password -> re-login).
-- JwtAuthenticationFilter.isIssuedBeforePasswordChange() then spuriously
-- treats that fresh token as predating the password change and rejects it
-- with 401, right after the user (or the must-change-password bootstrap flow)
-- changed their password. DATETIME(3) keeps millisecond precision so the
-- comparison never loses information.
ALTER TABLE users MODIFY COLUMN password_changed_at DATETIME(3) NULL;
