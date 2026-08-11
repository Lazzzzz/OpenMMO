-- Chat moderation is checked server-side for every message.
ALTER TABLE characters
  ADD COLUMN muted_until TIMESTAMP;
