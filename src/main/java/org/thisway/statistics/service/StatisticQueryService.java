package org.thisway.statistics.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.statistics.constant.StatisticConstants;
import org.thisway.statistics.dto.response.StatisticResponse;
import org.thisway.statistics.entity.Statistics;
import org.thisway.statistics.repository.StatisticsRepository;
import org.thisway.triplog.dto.response.TripLocationStats;
import org.thisway.triplog.repository.TripLogRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticQueryService {
    
    private final StatisticsRepository statisticsRepository;
    private final TripLogRepository tripLogRepository;
    
    /**
     * 출발지 통계 조회
     */
    public List<TripLocationStats> getStartLocationStatBetweenDates(Long companyId, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("출발지 통계 조회: 회사 ID {}, 시작 시간 {}, 종료 시간 {}", companyId, startTime, endTime);
        return tripLogRepository.countGroupedByOnAddr(companyId, startTime, endTime);
    }
    
    /**
     * 날짜 범위 기반 통계 조회
     * - DB에 저장된 통계 데이터를 조회해서 범위에 맞게 합산/평균 계산
     * - 실시간 계산이 아닌 저장된 데이터 활용으로 빠른 응답
     */
    public StatisticResponse getStatisticByDateRange(Long companyId, LocalDate startDate, LocalDate endDate) {
        log.info("날짜 범위 통계 조회: 회사 ID {}, 시작 날짜 {}, 종료 날짜 {}", companyId, startDate, endDate);
        
        // 1. 해당 날짜 범위의 저장된 통계 데이터들 조회
        List<Statistics> statisticsList = statisticsRepository.findByCompanyIdAndDateRange(companyId, startDate, endDate);
        
        if (statisticsList.isEmpty()) {
            throw new CustomException(ErrorCode.STATISTICS_NOT_FOUND);
        }
        
        // 2. 날짜 범위 계산
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        
        // 3. 합산 계산
        int totalPowerOnCount = statisticsList.stream()
            .mapToInt(Statistics::getPowerOnCount)
            .sum();
            
        int totalDrivingTime = statisticsList.stream()
            .mapToInt(Statistics::getTotalDrivingTime)
            .sum();
        
        // 4. 평균 계산
        double averageDailyPowerCount = (double) totalPowerOnCount / daysBetween;
        
        double averageOperationRate = statisticsList.stream()
            .mapToDouble(Statistics::getAverageOperationRate)
            .average()
            .orElse(StatisticConstants.DEFAULT_OPERATION_RATE);
        
        // 5. 시간대별 가동률 합산
        List<Integer> hourlyTotals = calculateHourlyTotalsFromStatistics(statisticsList);
        
        // 6. 피크/최소 시간 계산
        int peakHour = findExtremeHour(hourlyTotals, true);  // 최대값
        int lowHour = findExtremeHour(hourlyTotals, false);  // 최소값
        
        // 7. 통계 응답 생성
        String dateRange = startDate + StatisticConstants.DATE_RANGE_SEPARATOR + endDate;
        return StatisticResponse.fromAggregatedData(
            companyId, dateRange, Integer.valueOf(totalPowerOnCount), Double.valueOf(averageDailyPowerCount),
            Integer.valueOf(totalDrivingTime), Integer.valueOf(peakHour), Integer.valueOf(lowHour), Double.valueOf(averageOperationRate), hourlyTotals
        );
    }
    
    /**
     * 저장된 통계들에서 시간대별 가동률 합산
     */
    private List<Integer> calculateHourlyTotalsFromStatistics(List<Statistics> statisticsList) {
        int[] hourlyTotals = new int[StatisticConstants.HOURS_IN_DAY];
        
        for (Statistics stat : statisticsList) {
            Integer[] hourlyRates = stat.getHourlyRatesArray();
            for (int hour = 0; hour < StatisticConstants.HOURS_IN_DAY; hour++) {
                Integer rate = hourlyRates[hour];
                hourlyTotals[hour] += (rate != null ? rate : StatisticConstants.DEFAULT_HOURLY_RATE);
            }
        }
        
        List<Integer> result = new java.util.ArrayList<>();
        for (int i = 0; i < StatisticConstants.HOURS_IN_DAY; i++) {
            result.add(hourlyTotals[i]);
        }
        return result;
    }
    
    /**
     * 시간대별 가동률에서 극값(최대/최소) 시간대 찾기
     * @param hourlyTotals 시간대별 가동률 리스트
     * @param findMax true면 최대값, false면 최소값
     * @return 극값을 가진 시간대
     */
    private int findExtremeHour(List<Integer> hourlyTotals, boolean findMax) {
        int extremeHour = StatisticConstants.DEFAULT_PEAK_HOUR;
        int extremeValue = hourlyTotals.get(0) != null ?
                hourlyTotals.get(0) : StatisticConstants.DEFAULT_HOURLY_RATE;

        for (int hour = 1; hour < StatisticConstants.HOURS_IN_DAY; hour++) {
            int currentValue = hourlyTotals.get(hour) != null ?
                    hourlyTotals.get(hour) : StatisticConstants.DEFAULT_HOURLY_RATE;
            
            boolean shouldUpdate = findMax ? currentValue > extremeValue : currentValue < extremeValue;
            if (shouldUpdate) {
                extremeValue = currentValue;
                extremeHour = hour;
            }
        }
        
        return extremeHour;
    }
}
