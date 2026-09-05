-- Read-only known-difference audit. Run with the target database selected.
-- CHECKED means only this check passed; it NEVER authorizes baseline/migration.
SELECT 'geofence_time' AS check_name,
       CASE
         WHEN SUM(column_name='occured_time') > 0 AND SUM(column_name='occurred_time') > 0
           THEN 'STOP_AMBIGUOUS_COLUMNS'
         WHEN SUM(column_name='occured_time') > 0 THEN 'LEGACY_TYPO'
         WHEN SUM(column_name='occurred_time') > 0 THEN 'CHECKED'
         ELSE 'MISSING_COLUMN'
       END AS finding
FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='geofence_log'
UNION ALL
SELECT 'statistics_table', IF(COUNT(*)=1, 'CHECKED', 'MISSING_TABLE')
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name='statistics'
UNION ALL
SELECT 'trip_vehicle_index',
       IF(COUNT(*)=1, 'CHECKED', 'MISSING_OR_DIFFERENT_INDEX')
FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='trip_log'
  AND index_name='idx_trip_log_vehicle_id' AND column_name='vehicle_id' AND seq_in_index=1
UNION ALL
SELECT 'misplaced_trip_index', IF(COUNT(*)=0, 'CHECKED', 'LEGACY_WRONG_TABLE')
FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='power_log' AND index_name='idx_trip_log_vehicle_id'
UNION ALL
SELECT 'batch_table_set', IF(COUNT(*)=9, 'CHECKED', 'MISSING_BATCH_TABLES')
FROM information_schema.tables
WHERE table_schema=DATABASE() AND BINARY table_name IN (
  'BATCH_JOB_INSTANCE', 'BATCH_JOB_EXECUTION', 'BATCH_JOB_EXECUTION_PARAMS',
  'BATCH_STEP_EXECUTION', 'BATCH_STEP_EXECUTION_CONTEXT', 'BATCH_JOB_EXECUTION_CONTEXT',
  'BATCH_STEP_EXECUTION_SEQ', 'BATCH_JOB_EXECUTION_SEQ', 'BATCH_JOB_SEQ')
UNION ALL
SELECT 'flyway_history_table', IF(COUNT(*)=1, 'HISTORY_REQUIRES_REVIEW', 'NO_HISTORY')
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name='flyway_schema_history';
