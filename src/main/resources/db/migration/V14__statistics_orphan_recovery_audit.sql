-- Recovery is an explicit offline operation, never an age-based automatic takeover.
CREATE TABLE statistics_orphan_recovery_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_execution_id BIGINT NOT NULL,
    before_version BIGINT NOT NULL,
    after_version BIGINT NOT NULL,
    approval_id VARCHAR(64) NOT NULL,
    stopped_evidence_ref VARCHAR(160) NOT NULL,
    snapshot_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_json TEXT NOT NULL,
    recovered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_statistics_orphan_recovery_execution UNIQUE (job_execution_id),
    CONSTRAINT fk_statistics_orphan_recovery_execution FOREIGN KEY (job_execution_id)
        REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
);
