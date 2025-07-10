package org.thisway.company.statistics.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.company.statistics.StatisticConstants;
import org.thisway.company.statistics.infrastructure.StatisticsRepository;
import org.thisway.company.statistics.interfaces.StatisticResponse;
import org.thisway.triplog.dto.TripLocationRaw;
import org.thisway.triplog.dto.TripLocationStats;
import org.thisway.triplog.repository.TripLogRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticQueryService {

    private final StatisticsRepository statisticsRepository;
    private final TripLogRepository tripLogRepository;

    /**
     * 날짜 범위 기반 통계 조회
     * - DB에 저장된 통계 데이터를 조회해서 범위에 맞게 합산/평균 계산
     * - 실시간 계산이 아닌 저장된 데이터 활용으로 빠른 응답
     */
    public StatisticResponse getStatisticByDateRange(Long companyId, LocalDate startDate, LocalDate endDate) {
        log.info("날짜 범위 통계 조회: 회사 ID {}, 시작 날짜 {}, 종료 날짜 {}", companyId, startDate, endDate);

        // 1. 해당 날짜 범위의 저장된 통계 데이터들 조회
        List<Statistics> statisticsList = statisticsRepository.findByCompanyIdAndDateRange(companyId, startDate, endDate);

//        if (statisticsList.isEmpty()) {
//            throw new CustomException(ErrorCode.STATISTICS_NOT_FOUND);
//        }

        // 2. 날짜 범위 계산
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        // 3. 합산 계산 - Stream API 활용
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

        // 5. 시간대별 가동률 평균 계산
        List<Double> hourlyAverages = calculateHourlyAveragesFromStatistics(statisticsList);

        // 6. 피크/최소 시간 계산
        int peakHour = findExtremeHour(hourlyAverages, true);  // 최대값
        int lowHour = findExtremeHour(hourlyAverages, false);  // 최소값

        // 7. 시작 위치 통계
        LocalDateTime startTime = LocalDateTime.of(startDate, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(endDate, LocalTime.MAX);
        List<TripLocationStats> locationStats = getStartLocationStatBetweenDates(companyId, startTime, endTime);

        // 7. 통계 응답 생성
        String dateRange = startDate + StatisticConstants.DATE_RANGE_SEPARATOR + endDate;
        return StatisticResponse.fromAggregatedData(
                companyId, dateRange, totalPowerOnCount, averageDailyPowerCount,
                totalDrivingTime, peakHour, lowHour, averageOperationRate, hourlyAverages, locationStats
        );
    }

    /**
     * 저장된 통계들에서 시간대별 가동률 평균 계산
     */
    private List<Double> calculateHourlyAveragesFromStatistics(List<Statistics> statisticsList) {
        return IntStream.range(0, StatisticConstants.HOURS_IN_DAY)
                .mapToObj(hour -> {
                    double average = statisticsList.stream()
                            .mapToDouble(stat -> {
                                Double rate = stat.getHourlyRate(hour);
                                log.info("hour: {}, rate: {}", hour, rate);
                                return rate != null ? rate : StatisticConstants.DEFAULT_HOURLY_RATE;
                            })
                            .average()
                            .orElse(StatisticConstants.DEFAULT_OPERATION_RATE);
                    return average;
                })
                .collect(Collectors.toList());
    }

    /**
     * 시간대별 가동률에서 극값(최대/최소) 시간대 찾기 - 최적화
     *
     * @param hourlyAverages 시간대별 가동률 리스트
     * @param findMax        true면 최대값, false면 최소값
     * @return 극값을 가진 시간대
     */
    private int findExtremeHour(List<Double> hourlyAverages, boolean findMax) {
        if (findMax) {
            return IntStream.range(0, StatisticConstants.HOURS_IN_DAY)
                    .reduce((extremeHour, currentHour) -> {
                        double extremeValue = hourlyAverages.get(extremeHour);
                        double currentValue = hourlyAverages.get(currentHour);
                        return currentValue > extremeValue ? currentHour : extremeHour;
                    })
                    .orElse(StatisticConstants.DEFAULT_PEAK_HOUR);
        } else {
            // 최소값 찾기 - 0인 값 제외
            return IntStream.range(0, StatisticConstants.HOURS_IN_DAY)
                    .filter(hour -> hourlyAverages.get(hour) > 0)
                    .reduce((lowHour, currentHour) -> {
                        double lowValue = hourlyAverages.get(lowHour);
                        double currentValue = hourlyAverages.get(currentHour);
                        return currentValue < lowValue ? currentHour : lowHour;
                    })
                    .orElseGet(() -> {
                        return StatisticConstants.DEFAULT_LOW_HOUR;
                    });
        }
    }

    /**
     * 출발지 통계 조회
     */
    private List<TripLocationStats> getStartLocationStatBetweenDates(Long companyId, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("출발지 통계 조회: 회사 ID {}, 시작 시간 {}, 종료 시간 {}", companyId, startTime, endTime);
        List<TripLocationRaw> rawLocation = tripLogRepository.countGroupedByOnAddr(companyId, startTime, endTime);

        long total = rawLocation.stream().mapToLong(TripLocationRaw::count).sum();

        return rawLocation.stream()
                .map(raw -> TripLocationStats.from(raw, total))
                .limit(3)
                .toList();
    }
}
