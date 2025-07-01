package org.thisway.statistics.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.statistics.dto.response.StatisticResponse;
import org.thisway.triplog.dto.response.TripLocationStats;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StatisticService {
    
    private final StatisticPersistenceService persistenceService;
    private final StatisticQueryService queryService;
    
    /**
     * 통계 저장 (배치용)
     */
    public void saveStatistics(Long companyId, LocalDate targetDate) {
        log.info("통계 저장 요청: 회사 ID {}, 대상 날짜 {}", companyId, targetDate);
        persistenceService.saveStatistics(companyId, targetDate);
    }
    
    /**
     * 날짜 범위 기반 통계 조회
     */
    @Transactional(readOnly = true)
    public StatisticResponse getStatisticByDateRange(Long companyId, LocalDate startDate, LocalDate endDate) {
        log.info("날짜 범위 통계 조회 요청: 회사 ID {}, 시작 날짜 {}, 종료 날짜 {}", companyId, startDate, endDate);
        return queryService.getStatisticByDateRange(companyId, startDate, endDate);
    }
}
