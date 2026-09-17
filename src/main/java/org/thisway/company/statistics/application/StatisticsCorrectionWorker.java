package org.thisway.company.statistics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.company.infrastructure.CompanyRepository;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class StatisticsCorrectionWorker {
    private final CompanyRepository companies;
    private final JdbcTemplate jdbc;
    private final StatisticService statistics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(long companyId, LocalDate date) {
        companies.lockById(companyId).orElseThrow(() -> new IllegalArgumentException("Unknown company"));
        var pending = jdbc.query("""
                SELECT requested_generation, reason FROM statistics_correction_request
                WHERE company_id=? AND target_date=? AND requested_generation>completed_generation
                  AND next_attempt_at<=CURRENT_TIMESTAMP(6) FOR UPDATE
                """, (rs, row) -> new Pending(rs.getLong(1), rs.getString(2)), companyId, date);
        if (pending.isEmpty()) return false;
        var request = pending.getFirst();
        statistics.saveStatistics(companyId, date, "LATE_" + request.reason());
        jdbc.update("""
                UPDATE statistics_correction_request SET completed_generation=?, attempts=0,
                    completed_at=CURRENT_TIMESTAMP(6), last_error=NULL WHERE company_id=? AND target_date=?
                """, request.generation(), companyId, date);
        return true;
    }

    private record Pending(long generation, String reason) { }
}
