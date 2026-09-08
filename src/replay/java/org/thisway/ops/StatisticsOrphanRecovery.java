package org.thisway.ops;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Offline-only operator tool. Excluded from the web artifact; it cannot determine remote process liveness. */
public final class StatisticsOrphanRecovery {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> RUNNING = Set.of("STARTING", "STARTED", "STOPPING");
    private static final Set<String> TERMINAL_STEPS = Set.of("COMPLETED", "FAILED", "STOPPED");

    public record StepSnapshot(long id, long version, String name, String status, String endTime) { }
    public record Snapshot(long executionId, long instanceId, long latestExecutionId, long version,
                           String jobName, String targetDate, String status, String endTime,
                           List<StepSnapshot> steps) {
        public Snapshot { steps = List.copyOf(steps); }
        public String json() throws Exception { return JSON.writeValueAsString(this); }
        public String sha256() throws Exception {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json().getBytes(StandardCharsets.UTF_8)));
        }
    }

    /** This is an operator assertion backed by an external evidence reference, not automatic liveness proof. */
    public record OfflineConfirmation(String approvalId, String stoppedEvidenceRef, boolean allWritersStopped) {
        public OfflineConfirmation {
            if (!allWritersStopped || approvalId == null || !approvalId.matches("[A-Za-z0-9_-]{1,64}")
                    || stoppedEvidenceRef == null || !stoppedEvidenceRef.matches("[A-Za-z0-9_./:-]{1,160}")) {
                throw new IllegalArgumentException("Offline writer-stop confirmation and evidence reference required");
            }
        }
    }

    public static Snapshot preview(Connection connection, long executionId) throws Exception {
        requireOwnConnection(connection, executionId);
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        try {
            Snapshot snapshot = read(connection, executionId, false);
            connection.commit();
            return snapshot;
        } catch (Exception failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
            connection.setReadOnly(false);
        }
    }

    /** Atomically fails the exact orphan snapshot and writes an audit; never launches the replacement job. */
    public static void recover(Connection connection, long executionId, long expectedVersion, String expectedSha256,
                               OfflineConfirmation confirmation) throws Exception {
        requireOwnConnection(connection, executionId);
        if (confirmation == null || expectedVersion < 0 || expectedSha256 == null
                || !expectedSha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid recovery request");
        connection.setAutoCommit(false);
        try {
            Snapshot snapshot = read(connection, executionId, true);
            if (snapshot.version() != expectedVersion || !snapshot.sha256().equals(expectedSha256)) {
                throw new IllegalStateException("Execution snapshot changed; inspect again");
            }
            if (!snapshot.jobName().equals("statisticsJob") || snapshot.latestExecutionId() != executionId
                    || !RUNNING.contains(snapshot.status()) || snapshot.endTime() != null) {
                throw new IllegalStateException("Only the latest unfinished statistics execution can be recovered");
            }
            for (StepSnapshot step : snapshot.steps()) {
                if (RUNNING.contains(step.status()) && step.endTime() == null) {
                    try (var update = connection.prepareStatement("""
                            UPDATE BATCH_STEP_EXECUTION SET STATUS='FAILED',EXIT_CODE='FAILED',
                                EXIT_MESSAGE=?,END_TIME=CURRENT_TIMESTAMP(6),LAST_UPDATED=CURRENT_TIMESTAMP(6),VERSION=VERSION+1
                            WHERE STEP_EXECUTION_ID=? AND JOB_EXECUTION_ID=? AND VERSION=? AND STATUS=? AND END_TIME IS NULL
                            """)) {
                        update.setString(1, "Offline orphan recovery; approval=" + confirmation.approvalId());
                        update.setLong(2, step.id());
                        update.setLong(3, executionId);
                        update.setLong(4, step.version());
                        update.setString(5, step.status());
                        if (update.executeUpdate() != 1) throw new IllegalStateException("Step changed");
                    }
                } else if (!TERMINAL_STEPS.contains(step.status()) || step.endTime() == null) {
                    throw new IllegalStateException("Unsupported step state; manual investigation required");
                }
            }
            try (var update = connection.prepareStatement("""
                    UPDATE BATCH_JOB_EXECUTION SET STATUS='FAILED',EXIT_CODE='FAILED',EXIT_MESSAGE=?,
                        END_TIME=CURRENT_TIMESTAMP(6),LAST_UPDATED=CURRENT_TIMESTAMP(6),VERSION=VERSION+1
                    WHERE JOB_EXECUTION_ID=? AND VERSION=? AND STATUS=? AND END_TIME IS NULL
                    """)) {
                update.setString(1, "Offline orphan recovery; approval=" + confirmation.approvalId());
                update.setLong(2, executionId);
                update.setLong(3, expectedVersion);
                update.setString(4, snapshot.status());
                if (update.executeUpdate() != 1) throw new IllegalStateException("Execution changed");
            }
            try (var audit = connection.prepareStatement("""
                    INSERT INTO statistics_orphan_recovery_audit(job_execution_id,before_version,after_version,
                        approval_id,stopped_evidence_ref,snapshot_sha256,snapshot_json)
                    VALUES(?,?,?,?,?,?,?)
                    """)) {
                audit.setLong(1, executionId);
                audit.setLong(2, expectedVersion);
                audit.setLong(3, expectedVersion + 1);
                audit.setString(4, confirmation.approvalId());
                audit.setString(5, confirmation.stoppedEvidenceRef());
                audit.setString(6, expectedSha256);
                audit.setString(7, snapshot.json());
                audit.executeUpdate();
            }
            connection.commit();
        } catch (Exception failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static Snapshot read(Connection connection, long executionId, boolean lock) throws SQLException {
        long instanceId;
        String jobName;
        // Resolve the immutable parent ID first, then lock instance before execution rows.
        try (var find = connection.prepareStatement("""
                SELECT JOB_INSTANCE_ID FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=?
                """)) {
            find.setLong(1, executionId);
            try (var rows = find.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("Execution not found");
                instanceId = rows.getLong(1);
            }
        }
        try (var find = connection.prepareStatement("SELECT JOB_NAME FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID=?"
                + (lock ? " FOR UPDATE" : ""))) {
            find.setLong(1, instanceId);
            try (var rows = find.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Job instance not found");
                jobName = rows.getString(1);
            }
        }
        long latest = 0, version = -1;
        String status = null, endTime = null;
        try (var query = connection.prepareStatement("""
                SELECT JOB_EXECUTION_ID,VERSION,STATUS,END_TIME FROM BATCH_JOB_EXECUTION
                WHERE JOB_INSTANCE_ID=? ORDER BY JOB_EXECUTION_ID
                """ + (lock ? " FOR UPDATE" : ""))) {
            query.setLong(1, instanceId);
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    latest = rows.getLong(1);
                    if (latest == executionId) {
                        version = rows.getLong(2);
                        if (rows.wasNull()) throw new IllegalStateException("Missing execution version");
                        status = rows.getString(3);
                        endTime = textTimestamp(rows, 4);
                    }
                }
            }
        }
        if (version < 0 || status == null) throw new IllegalStateException("Invalid execution metadata");
        String date;
        try (var query = connection.prepareStatement("""
                SELECT PARAMETER_NAME,PARAMETER_TYPE,PARAMETER_VALUE,IDENTIFYING
                FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID=?
                """ + (lock ? " FOR UPDATE" : ""))) {
            query.setLong(1, executionId);
            try (var rows = query.executeQuery()) {
                if (!rows.next() || !"targetDate".equals(rows.getString(1))
                        || !"java.lang.String".equals(rows.getString(2)) || !"Y".equals(rows.getString(4))) {
                    throw new IllegalStateException("Unsupported legacy job parameters");
                }
                date = rows.getString(3);
                if (date == null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalStateException("Invalid target date");
                LocalDate.parse(date);
                if (rows.next()) throw new IllegalStateException("Unexpected extra job parameter");
            }
        }
        var steps = new ArrayList<StepSnapshot>();
        try (var query = connection.prepareStatement("""
                SELECT STEP_EXECUTION_ID,VERSION,STEP_NAME,STATUS,END_TIME FROM BATCH_STEP_EXECUTION
                WHERE JOB_EXECUTION_ID=? ORDER BY STEP_EXECUTION_ID
                """ + (lock ? " FOR UPDATE" : ""))) {
            query.setLong(1, executionId);
            try (var rows = query.executeQuery()) {
                while (rows.next()) steps.add(new StepSnapshot(rows.getLong(1), rows.getLong(2),
                        rows.getString(3), rows.getString(4), textTimestamp(rows, 5)));
            }
        }
        return new Snapshot(executionId, instanceId, latest, version, jobName, date, status, endTime, steps);
    }

    private static String textTimestamp(ResultSet rows, int column) throws SQLException {
        var timestamp = rows.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime().toString();
    }

    private static void requireOwnConnection(Connection connection, long executionId) throws SQLException {
        if (executionId <= 0 || !connection.getAutoCommit()) {
            throw new IllegalArgumentException("A dedicated autocommit connection and positive execution ID are required");
        }
    }

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("Usage: preview EXECUTION_ID | execute EXECUTION_ID EXPECTED_VERSION SHA256 APPROVAL_ID STOPPED_EVIDENCE_REF");
            System.out.println("Offline only: stop all batch writers, verify process/DB sessions, then set STATISTICS_RECOVERY_OFFLINE_CONFIRMED=yes.");
            return;
        }
        try {
            boolean preview = args.length == 2 && args[0].equals("preview");
            if (!preview && !(args.length == 6 && args[0].equals("execute"))) throw new IllegalArgumentException("Invalid arguments");
            OfflineConfirmation confirmation = preview ? null : new OfflineConfirmation(args[4], args[5],
                    "yes".equals(System.getenv("STATISTICS_RECOVERY_OFFLINE_CONFIRMED")));
            String url = System.getenv("STATISTICS_RECOVERY_JDBC_URL");
            if (url == null || !url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("MySQL configuration required");
            URI parsed = URI.create(url.substring("jdbc:".length()));
            if (!Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(parsed.getHost()) || parsed.getUserInfo() != null) {
                throw new IllegalArgumentException("Use an approved loopback database connection or tunnel");
            }
            var properties = new java.util.Properties();
            properties.setProperty("user", System.getenv("STATISTICS_RECOVERY_USERNAME"));
            properties.setProperty("password", System.getenv("STATISTICS_RECOVERY_PASSWORD"));
            properties.setProperty("connectTimeout", "5000");
            properties.setProperty("socketTimeout", "10000");
            try (var connection = DriverManager.getConnection(url, properties)) {
                long executionId = Long.parseLong(args[1]);
                if (preview) {
                    Snapshot snapshot = preview(connection, executionId);
                    System.out.println(snapshot.json());
                    System.out.println("sha256=" + snapshot.sha256());
                } else {
                    recover(connection, executionId, Long.parseLong(args[2]), args[3], confirmation);
                    System.out.println("RECOVERED executionId=" + executionId + "; restart the same targetDate through the controlled job launcher.");
                }
            }
        } catch (Exception failure) {
            // URLs, passwords, stored exception bodies and SQL details must not reach general command logs.
            System.err.println("Recovery stopped (" + failure.getClass().getSimpleName()
                    + "). Verify metadata/audit before retry; never infer that a writer is stopped from its age.");
            System.exit(1);
        }
    }
}
