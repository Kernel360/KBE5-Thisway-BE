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
                SELECT COUNT(*) FROM statistics_checkpoint c JOIN statistics s
                ON s.company_id=c.company_id AND DATE(s.date)=c.target_date
                WHERE c.job_instance_id=? AND c.company_id=? AND c.formula_version=? AND s.formula_version=?
                """, Integer.class, jobInstanceId, companyId,
                org.thisway.company.statistics.domain.Statistics.CURRENT_FORMULA_VERSION,
                org.thisway.company.statistics.domain.Statistics.CURRENT_FORMULA_VERSION);
        if (done != null && done > 0) return;
        statistics.saveStatistics(companyId, date);
        jdbc.update("""
                INSERT INTO statistics_checkpoint(job_instance_id, company_id, target_date, formula_version)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE formula_version=VALUES(formula_version), completed_at=CURRENT_TIMESTAMP(6)
                """, jobInstanceId, companyId, date,
                org.thisway.company.statistics.domain.Statistics.CURRENT_FORMULA_VERSION);
    }
}
