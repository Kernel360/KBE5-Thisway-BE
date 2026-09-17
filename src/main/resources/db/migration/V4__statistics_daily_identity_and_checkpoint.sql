-- Never deduplicate legacy rows automatically. Existing duplicate calendar dates fail this ALTER.
ALTER TABLE statistics
    ADD COLUMN statistic_day DATE GENERATED ALWAYS AS (DATE(`date`)) STORED,
    ADD CONSTRAINT uk_statistics_company_day UNIQUE (company_id, statistic_day);

CREATE TABLE statistics_checkpoint (
    job_instance_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    target_date DATE NOT NULL,
    completed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (job_instance_id, company_id),
    CONSTRAINT fk_statistics_checkpoint_job FOREIGN KEY (job_instance_id)
        REFERENCES BATCH_JOB_INSTANCE(JOB_INSTANCE_ID),
    CONSTRAINT fk_statistics_checkpoint_company FOREIGN KEY (company_id) REFERENCES company(id)
);
