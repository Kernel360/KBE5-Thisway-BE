package org.thisway.company.statistics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.company.infrastructure.CompanyRepository;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class StatisticsCorrectionRequests {
    private final CompanyRepository companies;
    private final JdbcTemplate jdbc;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void sourceChanged(StatisticsSourceChanged event) {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        LocalDate through = event.throughDate().isAfter(yesterday) ? yesterday : event.throughDate();
        if (event.fromDate().isAfter(through)) return;
        // Same lock as initial calculation: an event cannot miss a concurrently inserted daily row.
        companies.lockById(event.companyId()).orElseThrow(() -> new IllegalArgumentException("Unknown company"));
        // Current locking read avoids a stale REPEATABLE READ snapshot from the source transaction.
        var dates = jdbc.query("""
                SELECT DATE(date) FROM statistics WHERE company_id=? AND date>=? AND date<?
                ORDER BY date FOR UPDATE
                """, (rs, row) -> rs.getDate(1).toLocalDate(), event.companyId(),
                event.fromDate().atStartOfDay(), through.plusDays(1).atStartOfDay());
        for (LocalDate date : dates) {
            int updated = jdbc.update("""
                    UPDATE statistics_correction_request
                    SET requested_generation=requested_generation+1, reason=?, requested_at=CURRENT_TIMESTAMP(6),
                        attempts=0, next_attempt_at=CURRENT_TIMESTAMP(6), last_error=NULL
                    WHERE company_id=? AND target_date=?
                    """, event.reason(), event.companyId(), date);
            if (updated == 0) jdbc.update("""
                    INSERT INTO statistics_correction_request(company_id,target_date,requested_generation,
                        completed_generation,reason,attempts,next_attempt_at,requested_at)
                    VALUES (?,?,1,0,?,0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))
                    """, event.companyId(), date, event.reason());
        }
    }
}
