package org.thisway.statistics.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.log.repository.LogRepository;
import org.thisway.statistics.dto.response.StatisticResponse;
import org.thisway.statistics.entity.Statistics;
import org.thisway.statistics.repository.StatisticsRepository;
import org.thisway.triplog.dto.response.TripLocationStats;
import org.thisway.triplog.entity.TripLog;
import org.thisway.triplog.repository.TripLogRepository;
import org.thisway.vehicle.repository.VehicleRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class StatisticService {
    private final TripLogRepository tripLogRepository;
    private final StatisticsRepository statisticsRepository;
    private final VehicleRepository vehicleRepository;
    private final CompanyRepository companyRepository;
    private final LogRepository logRepository;

    @Transactional(readOnly = true)
    public List<TripLocationStats> getStartLocationStatBetweenDates(Long companyId, LocalDateTime startTime, LocalDateTime endTime) {
        return tripLogRepository.countGroupedByOnAddr(companyId, startTime, endTime);
    }

    /*
    배치 메서드: 통계 저장 (매일 새벽 2시에 실행)
    - 하루 단위로 모든 통계를 미리 계산해서 DB에 저장
    - TripLog 데이터를 기반으로 시동 횟수, 총 운전 시간, 피크 시간 등 모든 통계 계산
    - 중복 방지: 같은 회사ID + 날짜 조합이 있으면 업데이트, 없으면 신규 저장
    */
    public void saveStatistics(Long companyId, LocalDate targetDate) {
        System.out.println("=== saveStatistics 호출 ===");
        System.out.println("전달받은 targetDate: " + targetDate);
        
        // 1. 회사 정보 조회
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
        
        // 2. 해당 날짜의 시작과 끝 시간 설정
        LocalDateTime startDateTime = targetDate.atStartOfDay();
        LocalDateTime endDateTime = targetDate.atTime(23, 59, 59);
        
        System.out.println("계산된 시작 시간: " + startDateTime);
        System.out.println("계산된 종료 시간: " + endDateTime);
        
        // 3. 시동 횟수 계산 (TripLog 개수 = 시동 건 횟수)
        Long powerOnCount = tripLogRepository.countPowerOnByCompanyAndDateRange(companyId, startDateTime, endDateTime);
        
        // 4. 총 운전 시간 계산 (TODO: TripLog의 startTime ~ endTime 차이 합산)
        Integer totalDrivingTime = calculateTotalDrivingTime(companyId, startDateTime, endDateTime);
        
        // 5. 시간대별 가동률 계산
        Integer[] hourlyOperationRates = calculateHourlyOperationRates(companyId, startDateTime, endDateTime);
        
        // 6. 시간대별 가동률을 기반으로 피크/최소 시간대 계산
        Integer peakHour = calculatePeakHourFromRates(hourlyOperationRates);
        Integer lowHour = calculateLowHourFromRates(hourlyOperationRates);
        
        // 7. 평균 가동률 계산
        Double averageOperationRate = calculateAverageOperationRate(hourlyOperationRates);
        
        // 8. 기존 통계 데이터 확인 (중복 방지)
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime startOfNextDay = targetDate.plusDays(1).atStartOfDay();
        Optional<Statistics> existingStatistics = statisticsRepository.getStatisticByCompanyIdAndDate(companyId, startOfDay, startOfNextDay);
        
        if (existingStatistics.isPresent()) {
            // 기존 데이터가 있으면 업데이트
            Statistics existing = existingStatistics.get();
            updateExistingStatistics(existing, powerOnCount.intValue(), powerOnCount.doubleValue(), 
                totalDrivingTime, peakHour, lowHour, averageOperationRate);
            existing.updateHourlyRates(hourlyOperationRates);
            statisticsRepository.save(existing);
            System.out.println("기존 통계 업데이트 완료: " + companyId + ", " + targetDate);
        } else {
            // 기존 데이터가 없으면 신규 저장
            Statistics statistics = Statistics.builder()
                .company(company)
                .date(startDateTime)
                .powerOnCount(powerOnCount.intValue())
                .averageDailyPowerCount(powerOnCount.doubleValue())
                .totalDrivingTime(totalDrivingTime)
                .peakHour(peakHour)
                .lowHour(lowHour)
                .averageOperationRate(averageOperationRate)
                .hour00(hourlyOperationRates[0]).hour01(hourlyOperationRates[1]).hour02(hourlyOperationRates[2]).hour03(hourlyOperationRates[3])
                .hour04(hourlyOperationRates[4]).hour05(hourlyOperationRates[5]).hour06(hourlyOperationRates[6]).hour07(hourlyOperationRates[7])
                .hour08(hourlyOperationRates[8]).hour09(hourlyOperationRates[9]).hour10(hourlyOperationRates[10]).hour11(hourlyOperationRates[11])
                .hour12(hourlyOperationRates[12]).hour13(hourlyOperationRates[13]).hour14(hourlyOperationRates[14]).hour15(hourlyOperationRates[15])
                .hour16(hourlyOperationRates[16]).hour17(hourlyOperationRates[17]).hour18(hourlyOperationRates[18]).hour19(hourlyOperationRates[19])
                .hour20(hourlyOperationRates[20]).hour21(hourlyOperationRates[21]).hour22(hourlyOperationRates[22]).hour23(hourlyOperationRates[23])
                .build();
            
            statisticsRepository.save(statistics);
            System.out.println("신규 통계 저장 완료: " + companyId + ", " + targetDate);
        }
    }

    /**
     * 시간대별 가동률 계산
     * 공식: (시간 내 GPS 로그 개수) / (3600 * 업체 내 차량 대수)
     */
    private Integer[] calculateHourlyOperationRates(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        System.out.println("=== 시간대별 가동률 계산 시작 ===");
        System.out.println("전달받은 시작 시간: " + startDateTime);
        System.out.println("전달받은 종료 시간: " + endDateTime);
        
        // 1. 업체 내 차량 대수 조회
        long vehicleCount = vehicleRepository.countByCompanyIdAndActiveTrue(companyId);
        System.out.println("업체 내 차량 대수: " + vehicleCount);
        
        if (vehicleCount == 0) {
            // 차량이 없으면 모든 시간대 가동률 0
            System.out.println("차량이 없어서 모든 시간대 가동률을 0으로 설정");
            return new Integer[24]; // 기본값 0으로 초기화됨
        }
        
        // 2. 시간대별 GPS 로그 개수 조회
        Map<Integer, Long> hourlyGpsLogCounts = logRepository.countGpsLogsByCompanyAndHour(companyId, startDateTime, endDateTime);
        System.out.println("시간대별 GPS 로그 개수: " + hourlyGpsLogCounts);
        
        // 3. 시간대별 가동률 계산
        Integer[] hourlyRates = new Integer[24];
        for (int hour = 0; hour < 24; hour++) {
            Long gpsLogCount = hourlyGpsLogCounts.getOrDefault(hour, 0L);
            // 가동률 = (GPS 로그 개수) / (3600 * 차량 대수) * 100 (퍼센트)
            Double operationRate = (gpsLogCount.doubleValue() / (3600.0 * vehicleCount)) * 100;
            hourlyRates[hour] = operationRate.intValue(); // 소수점 버림
            
            if (gpsLogCount > 0) {
                System.out.println("시간 " + hour + "시: GPS 로그 " + gpsLogCount + "개, 가동률 " + operationRate.intValue() + "%");
            }
        }
        
        System.out.println("=== 시간대별 가동률 계산 완료 ===");
        return hourlyRates;
    }

    /**
     * 평균 가동률 계산 (시간대별 가동률의 평균)
     */
    private Double calculateAverageOperationRate(Integer[] hourlyOperationRates) {
        double sum = 0.0;
        int count = 0;
        
        for (Integer rate : hourlyOperationRates) {
            if (rate != null) {
                sum += rate;
                count++;
            }
        }
        
        return count > 0 ? sum / count : 0.0;
    }

    // 기존 통계 데이터 업데이트 헬퍼 메서드
    private void updateExistingStatistics(Statistics existing, Integer powerOnCount, Double averageDailyPowerCount,
                                        Integer totalDrivingTime, Integer peakHour, Integer lowHour, 
                                        Double averageOperationRate) {
        existing.updateStatistics(powerOnCount, averageDailyPowerCount, totalDrivingTime, 
                                peakHour, lowHour, averageOperationRate);
        // 시간대별 가동률도 업데이트 필요시 여기서 처리
        // existing.updateHourlyRates(hourlyRates);
    }

    /*
    사용자 API: 날짜 범위 기반 통계 조회
    - DB에 저장된 통계 데이터를 조회해서 범위에 맞게 합산/평균 계산
    - 실시간 계산이 아닌 저장된 데이터 활용으로 빠른 응답
    */
    @Transactional(readOnly = true)
    public StatisticResponse getStatisticByDateRange(Long companyId, LocalDate startDate, LocalDate endDate) {
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
            .orElse(0.0);
        
        // 5. 피크/최소 시간 계산 (빈도수 기준으로 다시 계산)
        int peakHour = calculatePeakHourFromStatistics(statisticsList);
        int lowHour = calculateLowHourFromStatistics(statisticsList);
        
        // 6. 시간대별 가동률 합산
        int[] hourlyTotals = new int[24];
        for (Statistics stat : statisticsList) {
            hourlyTotals[0] += stat.getHour00();
            hourlyTotals[1] += stat.getHour01();
            hourlyTotals[2] += stat.getHour02();
            hourlyTotals[3] += stat.getHour03();
            hourlyTotals[4] += stat.getHour04();
            hourlyTotals[5] += stat.getHour05();
            hourlyTotals[6] += stat.getHour06();
            hourlyTotals[7] += stat.getHour07();
            hourlyTotals[8] += stat.getHour08();
            hourlyTotals[9] += stat.getHour09();
            hourlyTotals[10] += stat.getHour10();
            hourlyTotals[11] += stat.getHour11();
            hourlyTotals[12] += stat.getHour12();
            hourlyTotals[13] += stat.getHour13();
            hourlyTotals[14] += stat.getHour14();
            hourlyTotals[15] += stat.getHour15();
            hourlyTotals[16] += stat.getHour16();
            hourlyTotals[17] += stat.getHour17();
            hourlyTotals[18] += stat.getHour18();
            hourlyTotals[19] += stat.getHour19();
            hourlyTotals[20] += stat.getHour20();
            hourlyTotals[21] += stat.getHour21();
            hourlyTotals[22] += stat.getHour22();
            hourlyTotals[23] += stat.getHour23();
        }
        
        // 7. 통계 응답 생성
        return new StatisticResponse(
            companyId,
            startDate + " ~ " + endDate,
            totalPowerOnCount,
            averageDailyPowerCount,
            totalDrivingTime,
            peakHour,
            lowHour,
            averageOperationRate,
            hourlyTotals[0], hourlyTotals[1], hourlyTotals[2], hourlyTotals[3],
            hourlyTotals[4], hourlyTotals[5], hourlyTotals[6], hourlyTotals[7],
            hourlyTotals[8], hourlyTotals[9], hourlyTotals[10], hourlyTotals[11],
            hourlyTotals[12], hourlyTotals[13], hourlyTotals[14], hourlyTotals[15],
            hourlyTotals[16], hourlyTotals[17], hourlyTotals[18], hourlyTotals[19],
            hourlyTotals[20], hourlyTotals[21], hourlyTotals[22], hourlyTotals[23]
        );
    }

    // TODO: 실제 구현 필요한 메서드들
    private Integer calculateTotalDrivingTime(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        // TripLog 목록을 가져와서 각각의 운행시간을 계산하여 합산
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
     * 저장된 통계들에서 피크 시간대 계산
     * 여러 날짜의 시간대별 가동률을 합산하여 가장 높은 시간대 반환
     */
    private int calculatePeakHourFromStatistics(List<Statistics> statisticsList) {
        int[] hourlyTotals = new int[24];
        
        // 모든 통계의 시간대별 가동률 합산
        for (Statistics stat : statisticsList) {
            hourlyTotals[0] += stat.getHour00();
            hourlyTotals[1] += stat.getHour01();
            hourlyTotals[2] += stat.getHour02();
            hourlyTotals[3] += stat.getHour03();
            hourlyTotals[4] += stat.getHour04();
            hourlyTotals[5] += stat.getHour05();
            hourlyTotals[6] += stat.getHour06();
            hourlyTotals[7] += stat.getHour07();
            hourlyTotals[8] += stat.getHour08();
            hourlyTotals[9] += stat.getHour09();
            hourlyTotals[10] += stat.getHour10();
            hourlyTotals[11] += stat.getHour11();
            hourlyTotals[12] += stat.getHour12();
            hourlyTotals[13] += stat.getHour13();
            hourlyTotals[14] += stat.getHour14();
            hourlyTotals[15] += stat.getHour15();
            hourlyTotals[16] += stat.getHour16();
            hourlyTotals[17] += stat.getHour17();
            hourlyTotals[18] += stat.getHour18();
            hourlyTotals[19] += stat.getHour19();
            hourlyTotals[20] += stat.getHour20();
            hourlyTotals[21] += stat.getHour21();
            hourlyTotals[22] += stat.getHour22();
            hourlyTotals[23] += stat.getHour23();
        }
        
        // 가장 높은 값의 시간대 찾기
        int peakHour = 0;
        int maxTotal = hourlyTotals[0];
        
        for (int hour = 1; hour < 24; hour++) {
            if (hourlyTotals[hour] > maxTotal) {
                maxTotal = hourlyTotals[hour];
                peakHour = hour;
            }
        }
        
        return peakHour;
    }
    
    /**
     * 저장된 통계들에서 최소 시간대 계산
     * 여러 날짜의 시간대별 가동률을 합산하여 가장 낮은 시간대 반환
     */
    private int calculateLowHourFromStatistics(List<Statistics> statisticsList) {
        int[] hourlyTotals = new int[24];
        
        // 모든 통계의 시간대별 가동률 합산
        for (Statistics stat : statisticsList) {
            hourlyTotals[0] += stat.getHour00();
            hourlyTotals[1] += stat.getHour01();
            hourlyTotals[2] += stat.getHour02();
            hourlyTotals[3] += stat.getHour03();
            hourlyTotals[4] += stat.getHour04();
            hourlyTotals[5] += stat.getHour05();
            hourlyTotals[6] += stat.getHour06();
            hourlyTotals[7] += stat.getHour07();
            hourlyTotals[8] += stat.getHour08();
            hourlyTotals[9] += stat.getHour09();
            hourlyTotals[10] += stat.getHour10();
            hourlyTotals[11] += stat.getHour11();
            hourlyTotals[12] += stat.getHour12();
            hourlyTotals[13] += stat.getHour13();
            hourlyTotals[14] += stat.getHour14();
            hourlyTotals[15] += stat.getHour15();
            hourlyTotals[16] += stat.getHour16();
            hourlyTotals[17] += stat.getHour17();
            hourlyTotals[18] += stat.getHour18();
            hourlyTotals[19] += stat.getHour19();
            hourlyTotals[20] += stat.getHour20();
            hourlyTotals[21] += stat.getHour21();
            hourlyTotals[22] += stat.getHour22();
            hourlyTotals[23] += stat.getHour23();
        }
        
        // 가장 낮은 값의 시간대 찾기
        int lowHour = 0;
        int minTotal = hourlyTotals[0];
        
        for (int hour = 1; hour < 24; hour++) {
            if (hourlyTotals[hour] < minTotal) {
                minTotal = hourlyTotals[hour];
                lowHour = hour;
            }
        }
        
        return lowHour;
    }

    /**
     * 시간대별 가동률에서 피크 시간대 계산
     * @param hourlyOperationRates 시간대별 가동률 배열 (0~23)
     * @return 가장 높은 가동률의 시간대 (0~23)
     */
    private Integer calculatePeakHourFromRates(Integer[] hourlyOperationRates) {
        int peakHour = 0;
        int maxRate = 0;
        
        for (int hour = 0; hour < 24; hour++) {
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
     * @param hourlyOperationRates 시간대별 가동률 배열 (0~23)
     * @return 가장 낮은 가동률의 시간대 (0~23)
     */
    private Integer calculateLowHourFromRates(Integer[] hourlyOperationRates) {
        int lowHour = 0;
        int minRate = Integer.MAX_VALUE;
        
        for (int hour = 0; hour < 24; hour++) {
            Integer rate = hourlyOperationRates[hour];
            if (rate != null && rate < minRate) {
                minRate = rate;
                lowHour = hour;
            }
        }
        
        return lowHour;
    }
}
