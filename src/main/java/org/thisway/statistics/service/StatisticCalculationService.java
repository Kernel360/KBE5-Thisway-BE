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
    public Integer[] calculateHourlyOperationRates(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        log.info(StatisticConstants.LOG_CALCULATION_START);
        log.info("회사 ID: {}, 시작 시간: {}, 종료 시간: {}", companyId, startDateTime, endDateTime);
        
        // 1. 업체 내 차량 대수 조회
        long vehicleCount = vehicleRepository.countByCompanyIdAndActiveTrue(companyId);
        log.info("업체 내 차량 대수: {}", vehicleCount);
        
        if (vehicleCount == 0) {
            log.info(StatisticConstants.LOG_NO_VEHICLES);
            Integer[] emptyRates = new Integer[StatisticConstants.HOURS_IN_DAY];
            for (int i = 0; i < StatisticConstants.HOURS_IN_DAY; i++) {
                emptyRates[i] = StatisticConstants.DEFAULT_HOURLY_RATE;
            }
            return emptyRates;
        }
        
        // 2. 시간대별 GPS 로그 개수 조회
        Map<Integer, Long> hourlyGpsLogCounts = logRepository.countGpsLogsByCompanyAndHour(companyId, startDateTime, endDateTime);
        log.info("시간대별 GPS 로그 개수: {}", hourlyGpsLogCounts);
        
        // 3. 시간대별 가동률 계산
        Integer[] hourlyRates = new Integer[StatisticConstants.HOURS_IN_DAY];
        log.info("=== 시간대별 가동률 계산 시작 ===");
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
        log.info(StatisticConstants.LOG_CALCULATION_COMPLETE);
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
    public Integer calculatePeakHourFromRates(Integer[] hourlyOperationRates) {
        int peakHour = StatisticConstants.DEFAULT_PEAK_HOUR;
        int maxRate = 0;
        
        for (int hour = 0; hour < StatisticConstants.HOURS_IN_DAY; hour++) {
            Integer rate = hourlyOperationRates[hour];
            if (rate != null && rate > maxRate) {
                maxRate = rate;
                peakHour = hour;
            }
        }
        
        return peakHour;
    }
    
    /**
     * 시간대별 가동률에서 최소 시간대 계산
     */
    public Integer calculateLowHourFromRates(Integer[] hourlyOperationRates) {
        int lowHour = StatisticConstants.DEFAULT_LOW_HOUR;
        int minRate = Integer.MAX_VALUE;
        
        for (int hour = 0; hour < StatisticConstants.HOURS_IN_DAY; hour++) {
            Integer rate = hourlyOperationRates[hour];
            if (rate != null && rate < minRate) {
                minRate = rate;
                lowHour = hour;
            }
        }
        
        return lowHour;
    }
    
    /**
     * 평균 가동률 계산 (시간대별 가동률의 평균)
     */
    public Double calculateAverageOperationRate(Integer[] hourlyOperationRates) {
        double sum = 0.0;
        int count = 0;
        
        for (Integer rate : hourlyOperationRates) {
            if (rate != null) {
                sum += rate;
                count++;
            }
        }
        
        return count > 0 ? sum / count : StatisticConstants.DEFAULT_OPERATION_RATE;
    }
    
    /**
     * 시동 횟수 계산
     */
    public Long calculatePowerOnCount(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return tripLogRepository.countPowerOnByCompanyAndDateRange(companyId, startDateTime, endDateTime);
    }
} 
