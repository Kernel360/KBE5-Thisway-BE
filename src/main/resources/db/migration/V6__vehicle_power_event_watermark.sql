-- Preserve legacy status/coordinates. Do not infer event time from processing timestamps.
ALTER TABLE vehicle ADD COLUMN last_power_event_time DATETIME(6) NULL;
