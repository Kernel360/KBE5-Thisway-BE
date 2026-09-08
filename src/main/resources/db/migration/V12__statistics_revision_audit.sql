-- Existing numbers stay untouched. Their revision-zero snapshot is captured on first meaningful correction.
ALTER TABLE statistics ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE statistics_revision (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    target_date DATE NOT NULL,
    revision BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    CONSTRAINT uk_statistics_revision UNIQUE (company_id,target_date,revision),
    CONSTRAINT fk_statistics_revision_company FOREIGN KEY (company_id) REFERENCES company(id)
);
