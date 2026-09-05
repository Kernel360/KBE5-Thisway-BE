-- Existing rows remain NULL: no unverified legacy backfill or deletion.
ALTER TABLE gps_log ADD COLUMN event_key BINARY(32) NULL;
CREATE UNIQUE INDEX uk_gps_log_event_key ON gps_log (event_key);
