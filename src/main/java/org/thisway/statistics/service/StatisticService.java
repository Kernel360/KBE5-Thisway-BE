package org.thisway.statistics.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.statistics.dto.response.StatisticResponse;
import org.thisway.statistics.entity.Statistics;
import org.thisway.statistics.repository.StatisticsRepository;
import org.thisway.triplog.dto.response.TripLocationStats;
import org.thisway.triplog.repository.TripLogRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class StatisticService {
    private final TripLogRepository tripLogRepository;
    private final StatisticsRepository statisticsRepository;
    private final CompanyRepository companyRepository;

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
        // 1. 회사 정보 조회
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
        
        // 2. 해당 날짜의 시작과 끝 시간 설정
        LocalDateTime startDateTime = targetDate.atStartOfDay();
        LocalDateTime endDateTime = targetDate.atTime(23, 59, 59);
        
        // 3. 시동 횟수 계산 (TripLog 개수 = 시동 건 횟수)
        Long powerOnCount = tripLogRepository.countPowerOnByCompanyAndDateRange(companyId, startDateTime, endDateTime);
        
        // 4. 총 운전 시간 계산 (TODO: TripLog의 startTime ~ endTime 차이 합산)
        Integer totalDrivingTime = calculateTotalDrivingTime(companyId, startDateTime, endDateTime);
        
        // 5. 피크/최소 시간대 계산 (TODO: 시간대별 운전 빈도 분석)
        Integer peakHour = calculatePeakHour(companyId, startDateTime, endDateTime);
        Integer lowHour = calculateLowHour(companyId, startDateTime, endDateTime);
        
        // 6. 평균 가동률 계산 (TODO: GPS 로그 기반)
        Double averageOperationRate = calculateAverageOperationRate(companyId, startDateTime, endDateTime);
        
        // 7. 시간대별 가동률 계산 (TODO: hour00 ~ hour23)
        // Integer[] hourlyOperationRates = calculateHourlyOperationRates(companyId, startDateTime, endDateTime);
        
        // 8. 기존 통계 데이터 확인 (중복 방지)
        Optional<Statistics> existingStatistics = statisticsRepository.getStatisticByCompanyIdAndDate(companyId, targetDate);
        
        if (existingStatistics.isPresent()) {
            // 기존 데이터가 있으면 업데이트
            Statistics existing = existingStatistics.get();
            updateExistingStatistics(existing, powerOnCount.intValue(), powerOnCount.doubleValue(), 
                totalDrivingTime, peakHour, lowHour, averageOperationRate);
            statisticsRepository.save(existing);
            System.out.println("기존 통계 업데이트 완료: " + companyId + ", " + targetDate);
        } else {
            // 기존 데이터가 없으면 신규 저장
            Statistics statistics = Statistics.builder()
                .company(company)
                .date(startDateTime)
                .powerOnCount(powerOnCount.intValue())
                .averageDailyPowerCount(powerOnCount.doubleValue()) // 하루 단위이므로 동일
                .totalDrivingTime(totalDrivingTime)
                .peakHour(peakHour)
                .lowHour(lowHour)
                .averageOperationRate(averageOperationRate)
                // 시간대별 가동률 (TODO: 실제 계산 값으로 대체)
                .hour00(0).hour01(0).hour02(0).hour03(0).hour04(0).hour05(0)
                .hour06(0).hour07(0).hour08(0).hour09(0).hour10(0).hour11(0)
                .hour12(0).hour13(0).hour14(0).hour15(0).hour16(0).hour17(0)
                .hour18(0).hour19(0).hour20(0).hour21(0).hour22(0).hour23(0)
                .build();
            
            statisticsRepository.save(statistics);
            System.out.println("신규 통계 저장 완료: " + companyId + ", " + targetDate);
        }
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
        // TripLog의 startTime과 endTime 차이를 분 단위로 합산
        return 0;
    }
    
    private Integer calculatePeakHour(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        // 시간대별 TripLog 빈도 분석해서 가장 많은 시간대 반환
        return 0;
    }
    
    private Integer calculateLowHour(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        // 시간대별 TripLog 빈도 분석해서 가장 적은 시간대 반환
        return 0;
    }
    
    private Double calculateAverageOperationRate(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        // GPS 로그 기반 평균 가동률 계산
        return 0.0;
    }
    
    private int calculatePeakHourFromStatistics(List<Statistics> statisticsList) {
        // 저장된 통계들에서 시간대별 합산해서 피크 시간 계산
        return 0;
    }
    
    private int calculateLowHourFromStatistics(List<Statistics> statisticsList) {
        // 저장된 통계들에서 시간대별 합산해서 최소 시간 계산
        return 0;
    }
}
