CREATE TABLE device_credential (
    emulator_id BIGINT NOT NULL PRIMARY KEY,
    key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    bound_vehicle_id BIGINT NOT NULL,
    bound_company_id BIGINT NOT NULL,
    bound_mdn VARCHAR(20) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    CONSTRAINT fk_device_credential_emulator FOREIGN KEY (emulator_id) REFERENCES emulator(id) ON DELETE CASCADE,
    CONSTRAINT uk_device_credential_hash UNIQUE (key_hash),
    CONSTRAINT ck_device_credential_expiry CHECK (expires_at > issued_at)
);

-- IDs are historical references, not cascade FKs: deleting a device must not erase its audit.
CREATE TABLE device_credential_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    emulator_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    actor_member_id BIGINT NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    INDEX idx_device_credential_event (emulator_id, id)
);
