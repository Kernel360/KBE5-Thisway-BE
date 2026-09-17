CREATE TABLE statistics_correction_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    target_date DATE NOT NULL,
    requested_generation BIGINT NOT NULL,
    completed_generation BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    last_error VARCHAR(120) NULL,
    CONSTRAINT uk_statistics_correction_day UNIQUE (company_id, target_date),
    CONSTRAINT fk_statistics_correction_company FOREIGN KEY (company_id) REFERENCES company(id),
    INDEX ix_statistics_correction_due (next_attempt_at, id)
);
