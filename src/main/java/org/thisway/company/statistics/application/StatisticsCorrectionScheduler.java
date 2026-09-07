package org.thisway.company.statistics.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsCorrectionScheduler {
    private final JdbcTemplate jdbc;
    private final StatisticsCorrectionWorker worker;
    private final io.micrometer.core.instrument.MeterRegistry meters;

    @Scheduled(cron = "${thisway.statistics.correction-cron:0 */5 * * * ?}", zone = "Asia/Seoul")
    public void drain() {
        var keys = jdbc.query("""
                SELECT company_id, target_date FROM statistics_correction_request
                WHERE requested_generation>completed_generation AND next_attempt_at<=CURRENT_TIMESTAMP(6)
                ORDER BY next_attempt_at, id LIMIT 20
                """, (rs, row) -> new Key(rs.getLong(1), rs.getDate(2).toLocalDate()));
        for (var key : keys) {
            try {
                boolean processed = worker.process(key.companyId(), key.date());
                meters.counter("thisway.statistics.correction", "outcome", processed ? "processed" : "noop").increment();
            } catch (RuntimeException failure) {
                meters.counter("thisway.statistics.correction", "outcome", "failed").increment();
                // The worker has rolled back. Persist retry state in a separate short statement.
                jdbc.update("""
                        UPDATE statistics_correction_request SET
                            next_attempt_at=TIMESTAMPADD(SECOND,LEAST(3600,30*POW(2,LEAST(attempts,7))),CURRENT_TIMESTAMP(6)),
                            attempts=LEAST(attempts+1,1000000),
                            last_error=? WHERE company_id=? AND target_date=?
                            AND requested_generation>completed_generation
                        """, failure.getClass().getSimpleName(), key.companyId(), key.date());
                log.warn("Statistics correction failed: companyId={}, targetDate={}, errorType={}",
                        key.companyId(), key.date(), failure.getClass().getSimpleName());
            }
        }
    }

    private record Key(long companyId, LocalDate date) { }
}
