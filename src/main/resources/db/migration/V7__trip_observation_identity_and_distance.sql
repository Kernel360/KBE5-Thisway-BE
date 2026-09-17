-- Legacy mixed distances and duplicate trips remain untouched and unclaimed (NULL identity).
ALTER TABLE trip_log
    ADD COLUMN identity_start_time DATETIME(6) NULL,
    ADD COLUMN start_odometer INT NULL,
    ADD COLUMN end_odometer INT NULL,
    ADD COLUMN distance_meters INT NULL,
    ADD INDEX idx_trip_vehicle_start (vehicle_id, start_time),
    ADD CONSTRAINT uk_trip_observed_start UNIQUE (vehicle_id, identity_start_time),
    ADD CONSTRAINT ck_trip_observation CHECK (
        identity_start_time IS NULL OR (
            identity_start_time = start_time
            AND (start_odometer IS NOT NULL OR end_odometer IS NOT NULL)
            AND (start_odometer IS NULL OR start_odometer >= 0)
            AND (end_odometer IS NULL OR end_odometer >= 0)
            AND ((end_odometer IS NULL AND end_time IS NULL AND active = 0)
                OR (end_odometer IS NOT NULL AND end_time IS NOT NULL AND end_time >= start_time AND active = 1))
            AND (distance_meters <=> CASE
                WHEN start_odometer IS NOT NULL AND end_odometer IS NOT NULL AND end_odometer >= start_odometer
                THEN end_odometer - start_odometer ELSE NULL END)
        )
    );
