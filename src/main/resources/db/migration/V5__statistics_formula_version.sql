-- Preserve old GPS-based numbers as v1. Never relabel them as completed-trip time.
ALTER TABLE statistics
    ADD COLUMN formula_version INT NOT NULL DEFAULT 1,
    ADD COLUMN fleet_vehicle_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN gps_observation_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN unclosed_trip_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN calculated_at DATETIME(6) NULL;
ALTER TABLE statistics_checkpoint ADD COLUMN formula_version INT NOT NULL DEFAULT 1;
