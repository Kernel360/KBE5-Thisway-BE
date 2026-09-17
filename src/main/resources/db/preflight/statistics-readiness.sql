-- Read-only. Run on a restored snapshot before V4. Any rows require manual investigation, not DELETE.
SELECT company_id, DATE(`date`) AS statistic_day, COUNT(*) AS duplicate_count
FROM statistics
GROUP BY company_id, DATE(`date`)
HAVING COUNT(*) > 1;

-- Historic timestamp-keyed instances cannot be restarted under the targetDate validator.
SELECT i.JOB_INSTANCE_ID, i.JOB_NAME, e.JOB_EXECUTION_ID, e.STATUS
FROM BATCH_JOB_INSTANCE i JOIN BATCH_JOB_EXECUTION e ON e.JOB_INSTANCE_ID=i.JOB_INSTANCE_ID
WHERE i.JOB_NAME='statisticsJob' AND e.STATUS IN ('STARTING','STARTED','STOPPING','UNKNOWN');
