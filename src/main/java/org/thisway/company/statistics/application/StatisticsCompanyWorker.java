package org.thisway.company.statistics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import java.time.LocalDate;

/** A business checkpoint commits atomically with the company's daily aggregate. */
@Service
@RequiredArgsConstructor
public class StatisticsCompanyWorker {
    private final CompanyRepository companies;
    private final StatisticService statistics;
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(long jobInstanceId, long companyId, LocalDate date) {
        companies.lockById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
        Integer done = jdbc.queryForObject("""
                SELECT COUNT(*) FROM statistics_checkpoint WHERE job_instance_id=? AND company_id=?
                """, Integer.class, jobInstanceId, companyId);
        if (done != null && done > 0) return;
        statistics.saveStatistics(companyId, date);
        jdbc.update("""
                INSERT INTO statistics_checkpoint(job_instance_id, company_id, target_date)
                VALUES (?, ?, ?)
                """, jobInstanceId, companyId, date);
    }
}
