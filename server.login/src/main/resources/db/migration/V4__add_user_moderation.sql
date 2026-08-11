-- Temporary bans are enforced by the login server. Null means the account is not banned.
ALTER TABLE users
  ADD COLUMN banned_until TIMESTAMP,
  ADD COLUMN ban_reason VARCHAR(255);
