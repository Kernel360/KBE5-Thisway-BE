ALTER TABLE emulator ADD COLUMN assignment_revision BIGINT NOT NULL DEFAULT 0;
-- Existing V8 keys retain their initial binding; no key material or device data is rewritten.
ALTER TABLE device_credential ADD COLUMN bound_assignment_revision BIGINT NOT NULL DEFAULT 0;
