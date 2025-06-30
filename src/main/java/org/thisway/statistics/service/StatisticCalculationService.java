package org.thisway.statistics.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.log.repository.LogRepository;
import org.thisway.statistics.constant.StatisticConstants;
import org.thisway.triplog.entity.TripLog;
import org.thisway.triplog.repository.TripLogRepository;
import org.thisway.vehicle.repository.VehicleRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticCalculationService {
    
    private final TripLogRepository tripLogRepository;
    private final VehicleRepository vehicleRepository;
    private final LogRepository logRepository;
    
    /**
     * 시간대별 가동률 계산
     * 공식: (시간 내 GPS 로그 개수) / (3600 * 업체 내 차량 대수) * 100
     */
    public int[] calculateHourlyOperationRates(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        log.info("=== 시간대별 가동률 계산 시작 ===");
        log.info("회사 ID: {}, 시작 시간: {}, 종료 시간: {}", companyId, startDateTime, endDateTime);

        // 1. 업체 내 차량 대수 조회
        long vehicleCount = vehicleRepository.countByCompanyIdAndActiveTrue(companyId);
        log.info("업체 내 차량 대수: {}", vehicleCount);

        if (vehicleCount == 0) {
            log.info("차량이 없어서 모든 시간대 가동률을 0으로 설정");
            return new int[StatisticConstants.HOURS_IN_DAY];
        }

        // 2. 시간대별 GPS 로그 개수 조회
        Map<Integer, Long> hourlyGpsLogCounts = logRepository.countGpsLogsByCompanyAndHour(companyId, startDateTime, endDateTime);
        log.info("시간대별 GPS 로그 개수: {}", hourlyGpsLogCounts);

        // 3. 시간대별 가동률 계산
        int[] hourlyRates = new int[StatisticConstants.HOURS_IN_DAY];
        log.info("차량 대수: {}, 3600초: {}, 100%: {}", vehicleCount, StatisticConstants.SECONDS_IN_HOUR, StatisticConstants.PERCENTAGE_MULTIPLIER);

        for (int hour = 0; hour < StatisticConstants.HOURS_IN_DAY; hour++) {
            Long gpsLogCount = hourlyGpsLogCounts.getOrDefault(hour, 0L);

            // 가동률 = (GPS 로그 개수) / (3600 * 차량 대수) * 100 (퍼센트)
            double denominator = StatisticConstants.SECONDS_IN_HOUR * vehicleCount;
            double operationRate = (gpsLogCount.doubleValue() / denominator) * StatisticConstants.PERCENTAGE_MULTIPLIER;
            hourlyRates[hour] = (int) operationRate;

            log.info("시간 {}시: GPS 로그 {}개, 분모 {}, 가동률 {}% (소수점: {})",
                hour, gpsLogCount, denominator, hourlyRates[hour], operationRate);
        }

        log.info("=== 시간대별 가동률 계산 완료 ===");
        log.info("계산된 배열: {}", java.util.Arrays.toString(hourlyRates));
        return hourlyRates;
    }
    
    /**
     * 총 운전 시간 계산 (분 단위)
     */
    public Integer calculateTotalDrivingTime(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        List<TripLog> tripLogs = tripLogRepository.findTripLogsByCompanyAndDateRange(companyId, startDateTime, endDateTime);
        
        long totalMinutes = tripLogs.stream()
                .mapToLong(tripLog -> {
                    Duration duration = Duration.between(tripLog.getStartTime(), tripLog.getEndTime());
                    return duration.toMinutes();
                })
                .sum();
                
        return (int) totalMinutes;
    }
    
    /**
     * 시간대별 가동률에서 피크 시간대 계산
     */
    public Integer calculatePeakHourFromRates(int[] hourlyOperationRates) {
        int peakHour = StatisticConstants.DEFAULT_PEAK_HOUR;
        int maxRate = 0;

        for (int hour = 0; hour < StatisticConstants.HOURS_IN_DAY; hour++) {
            int rate = hourlyOperationRates[hour];
            if (rate > maxRate) {
                maxRate = rate;
                peakHour = hour;
            }
        }

        return peakHour;
    }

    /**
     * 시간대별 가동률에서 최소 시간대 계산
     */
    public Integer calculateLowHourFromRates(int[] hourlyOperationRates) {
        int lowHour = StatisticConstants.DEFAULT_LOW_HOUR;
        int minRate = Integer.MAX_VALUE;
        boolean foundNonZero = false;

        for (int hour = 0; hour < StatisticConstants.HOURS_IN_DAY; hour++) {
            int rate = hourlyOperationRates[hour];
            
            // 0인 시간대는 제외하고 가장 낮은 값 찾기
            if (rate > 0 && rate < minRate) {
                minRate = rate;
                lowHour = hour;
                foundNonZero = true;
            }
        }

        if (!foundNonZero) {
            return StatisticConstants.DEFAULT_LOW_HOUR;
        }

        log.info("최저 가동률 시간대: {}시 (가동률: {}%)", lowHour, minRate);
        return lowHour;
    }
    
    /**
     * 평균 가동률 계산 (시간대별 가동률의 평균)
     */
    public Double calculateAverageOperationRate(int[] hourlyOperationRates) {
        double sum = 0.0;
        
        for (int rate : hourlyOperationRates) {
            sum += rate;
        }
        
        return sum / StatisticConstants.HOURS_IN_DAY;
    }
    
    /**
     * 시동 횟수 계산
     */
    public Long calculatePowerOnCount(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return tripLogRepository.countPowerOnByCompanyAndDateRange(companyId, startDateTime, endDateTime);
    }
}
