-- Coordinates remain in trip_log; retry metadata never copies location or HTTP error bodies.
CREATE TABLE trip_address_retry (
    trip_id BIGINT NOT NULL,
    side VARCHAR(3) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    claim_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_until DATETIME(6) NULL,
    PRIMARY KEY (trip_id, side),
    INDEX idx_trip_address_retry_due (status, next_attempt_at),
    INDEX idx_trip_address_retry_lease (status, lease_until),
    CONSTRAINT fk_trip_address_retry_trip FOREIGN KEY (trip_id) REFERENCES trip_log(id) ON DELETE CASCADE,
    CONSTRAINT ck_trip_address_retry_side CHECK (side IN ('on', 'off')),
    CONSTRAINT ck_trip_address_retry_status CHECK (status IN ('PENDING', 'RUNNING', 'EXHAUSTED')),
    CONSTRAINT ck_trip_address_retry_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_trip_address_retry_lease CHECK (
        (status = 'RUNNING' AND claim_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'RUNNING' AND claim_token IS NULL AND lease_until IS NULL))
);

-- A fixed high watermark bounds each sweep even while new trips continue to arrive.
CREATE TABLE trip_address_scan_state (
    id INT NOT NULL PRIMARY KEY,
    last_trip_id BIGINT NOT NULL DEFAULT 0,
    high_watermark BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trip_address_scan_singleton CHECK (id = 1)
);
INSERT INTO trip_address_scan_state (id) VALUES (1);
