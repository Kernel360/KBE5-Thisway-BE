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

/**
 * 통계 서비스 Facade
 * - 분리된 서비스들을 조율하는 역할
 * - 클라이언트에게 단일 진입점 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StatisticService {
    
    private final StatisticPersistenceService persistenceService;
    private final StatisticQueryService queryService;
    
    /**
     * 출발지 통계 조회
     */
    @Transactional(readOnly = true)
    public List<TripLocationStats> getStartLocationStatBetweenDates(Long companyId, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("출발지 통계 조회 요청: 회사 ID {}, 시작 시간 {}, 종료 시간 {}", companyId, startTime, endTime);
        return queryService.getStartLocationStatBetweenDates(companyId, startTime, endTime);
    }
    
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
