-- The control panel can suspend an account without deleting its characters or audit history.
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
