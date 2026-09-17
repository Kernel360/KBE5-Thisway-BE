package org.thisway.vehicle.triplog.application;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Bounded database discovery and leased retries; external I/O never holds a transaction or row lock. */
@Slf4j
@Component
public class TripAddressWorker {
    private final JdbcTemplate jdbc;
    private final TripAddressEnrichment enrichment;
    private final TransactionTemplate write;
    private final MeterRegistry metrics;
    private final int scanBatchSize;
    private final int maxLookups;
    private final int maxAttempts;
    private final int initialBackoffSeconds;
    private final int maxBackoffSeconds;
    private final int leaseSeconds;

    public TripAddressWorker(JdbcTemplate jdbc, TripAddressEnrichment enrichment,
                             PlatformTransactionManager transactions, MeterRegistry metrics,
                             @Value("${thisway.trip-address.worker.scan-batch-size:100}") int scanBatchSize,
                             @Value("${thisway.trip-address.worker.max-lookups:20}") int maxLookups,
                             @Value("${thisway.trip-address.worker.max-attempts:5}") int maxAttempts,
                             @Value("${thisway.trip-address.worker.initial-backoff-seconds:60}") int initialBackoffSeconds,
                             @Value("${thisway.trip-address.worker.max-backoff-seconds:3600}") int maxBackoffSeconds,
                             @Value("${thisway.trip-address.worker.lease-seconds:30}") int leaseSeconds) {
        if (scanBatchSize < 1 || scanBatchSize > 1000 || maxLookups < 1 || maxLookups > 100
                || maxAttempts < 1 || maxAttempts > 20 || initialBackoffSeconds < 1
                || maxBackoffSeconds < initialBackoffSeconds || maxBackoffSeconds > 86400
                || leaseSeconds < 5 || leaseSeconds > 3600) {
            throw new IllegalArgumentException("Invalid trip address worker bounds");
        }
        this.jdbc = jdbc;
        this.enrichment = enrichment;
        this.metrics = metrics;
        this.write = new TransactionTemplate(transactions);
        this.write.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.scanBatchSize = scanBatchSize;
        this.maxLookups = maxLookups;
        this.maxAttempts = maxAttempts;
        this.initialBackoffSeconds = initialBackoffSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
        this.leaseSeconds = leaseSeconds;
    }

    /** Disabled by default, including tests. Configure a cron expression to opt in. */
    @Scheduled(cron = "${thisway.trip-address.worker.cron:-}", zone = "UTC")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void scheduled() {
        try {
            runOnce();
        } catch (RuntimeException failure) {
            count("worker_failure", 1);
            log.warn("운행 주소 worker 보류: failureType={}", failure.getClass().getSimpleName());
        }
    }

    /** Internal one-shot entry point. Lease and backoff are measured using the database UTC clock. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunResult runOnce() {
        int discovered = discover();
        int exhausted = jdbc.update("""
                UPDATE trip_address_retry SET status='EXHAUSTED',claim_token=NULL,lease_until=NULL
                WHERE attempts>=? AND (status='PENDING' OR (status='RUNNING' AND lease_until<=UTC_TIMESTAMP(6)))
                LIMIT ?
                """, maxAttempts, maxLookups);
        int claimed = 0, updated = 0, noLongerNeeded = 0, deferred = 0;
        for (int i = 0; i < maxLookups; i++) {
            Claim claim = claim();
            if (claim == null) break;
            claimed++;
            // claim() has committed before invoking the separate NOT_SUPPORTED Spring proxy.
            var outcome = enrichment.enrichWithOutcome(claim.tripId(), claim.off());
            if (outcome != TripAddressEnrichment.Outcome.RETRY) {
                int removed = jdbc.update("DELETE FROM trip_address_retry WHERE trip_id=? AND side=? AND claim_token=?",
                        claim.tripId(), claim.side(), claim.token());
                if (removed == 1) {
                    if (outcome == TripAddressEnrichment.Outcome.UPDATED) updated++;
                    else noLongerNeeded++;
                }
            } else {
                boolean lastAttempt = claim.attempts() >= maxAttempts;
                int changed = jdbc.update("""
                        UPDATE trip_address_retry SET status=?,claim_token=NULL,lease_until=NULL,
                            next_attempt_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6))
                        WHERE trip_id=? AND side=? AND claim_token=?
                        """, lastAttempt ? "EXHAUSTED" : "PENDING", backoffSeconds(claim.attempts()),
                        claim.tripId(), claim.side(), claim.token());
                if (changed == 1) {
                    if (lastAttempt) exhausted++;
                    else deferred++;
                }
            }
        }
        count("discovered", discovered);
        count("claimed", claimed);
        count("updated", updated);
        count("no_longer_needed", noLongerNeeded);
        count("deferred", deferred);
        count("exhausted", exhausted);
        return new RunResult(discovered, claimed, updated, noLongerNeeded, deferred, exhausted);
    }

    private int discover() {
        return write.execute(status -> {
            long[] cursor = jdbc.queryForObject("""
                    SELECT last_trip_id,high_watermark FROM trip_address_scan_state WHERE id=1 FOR UPDATE
                    """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)});
            long after = cursor[0];
            long high = cursor[1];
            if (after >= high) {
                after = 0;
                high = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM trip_log", Long.class);
            }
            var ids = jdbc.queryForList("SELECT id FROM trip_log WHERE id>? AND id<=? ORDER BY id LIMIT ?",
                    Long.class, after, high, scanBatchSize);
            long next = ids.isEmpty() ? high : ids.getLast();
            int discovered = 0;
            for (String side : new String[]{"on", "off"}) {
                // Fixed identifiers only. Existing retry/exhausted state is retained on rediscovery.
                discovered += jdbc.update("INSERT INTO trip_address_retry(trip_id,side,next_attempt_at) "
                        + "SELECT id,?,UTC_TIMESTAMP(6) FROM trip_log WHERE id>? AND id<=? AND "
                        + side + "_addr IS NULL AND " + side + "_latitude IS NOT NULL AND "
                        + side + "_longitude IS NOT NULL AND NOT EXISTS (SELECT 1 FROM trip_address_retry r "
                        + "WHERE r.trip_id=trip_log.id AND r.side=?) "
                        + "ON DUPLICATE KEY UPDATE trip_id=trip_address_retry.trip_id",
                        side, after, next, side);
            }
            jdbc.update("UPDATE trip_address_scan_state SET last_trip_id=?,high_watermark=? WHERE id=1", next, high);
            return discovered;
        });
    }

    private Claim claim() {
        return write.execute(status -> {
            var due = jdbc.query("""
                    SELECT trip_id,side,attempts FROM trip_address_retry
                    WHERE attempts<? AND ((status='PENDING' AND next_attempt_at<=UTC_TIMESTAMP(6))
                        OR (status='RUNNING' AND lease_until<=UTC_TIMESTAMP(6)))
                    ORDER BY next_attempt_at,trip_id,side LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, (rs, row) -> new Claim(rs.getLong(1), rs.getString(2), rs.getInt(3) + 1,
                    UUID.randomUUID().toString()), maxAttempts);
            if (due.isEmpty()) return null;
            Claim claim = due.getFirst();
            jdbc.update("""
                    UPDATE trip_address_retry SET status='RUNNING',attempts=?,claim_token=?,
                        lease_until=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) WHERE trip_id=? AND side=?
                    """, claim.attempts(), claim.token(), leaseSeconds, claim.tripId(), claim.side());
            return claim;
        });
    }

    int backoffSeconds(int attempts) {
        return (int) Math.min(maxBackoffSeconds, (long) initialBackoffSeconds << Math.min(attempts - 1, 20));
    }

    private void count(String outcome, int amount) {
        if (amount > 0) metrics.counter("thisway.trip.address.worker", "outcome", outcome).increment(amount);
    }

    public record RunResult(int discovered, int claimed, int updated, int noLongerNeeded, int deferred, int exhausted) { }
    private record Claim(long tripId, String side, int attempts, String token) {
        boolean off() { return side.equals("off"); }
    }
}
