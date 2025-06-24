package org.thisway.statistics.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.statistics.constant.StatisticConstants;
import org.thisway.statistics.entity.Statistics;
import org.thisway.statistics.repository.StatisticsRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StatisticPersistenceService {
    
    private final StatisticsRepository statisticsRepository;
    private final CompanyRepository companyRepository;
    private final StatisticCalculationService calculationService;
    
    /**
     * 통계 저장 (배치용)
     * - 하루 단위로 모든 통계를 미리 계산해서 DB에 저장
     * - 중복 방지: 같은 회사ID + 날짜 조합이 있으면 업데이트, 없으면 신규 저장
     */
    public void saveStatistics(Long companyId, LocalDate targetDate) {
        log.info(StatisticConstants.LOG_SAVE_STATISTICS_CALL);
        log.info("회사 ID: {}, 대상 날짜: {}", companyId, targetDate);
        
        // 1. 회사 정보 조회
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
        
        // 2. 해당 날짜의 시작과 끝 시간 설정
        LocalDateTime startDateTime = targetDate.atStartOfDay();
        LocalDateTime endDateTime = targetDate.atTime(23, 59, 59);
        
        log.info("계산된 시작 시간: {}, 종료 시간: {}", startDateTime, endDateTime);
        
        // 3. 통계 계산
        Long powerOnCount = calculationService.calculatePowerOnCount(companyId, startDateTime, endDateTime);
        Integer totalDrivingTime = calculationService.calculateTotalDrivingTime(companyId, startDateTime, endDateTime);
        Integer[] hourlyOperationRates = calculationService.calculateHourlyOperationRates(companyId, startDateTime, endDateTime);
        Integer peakHour = calculationService.calculatePeakHourFromRates(hourlyOperationRates);
        Integer lowHour = calculationService.calculateLowHourFromRates(hourlyOperationRates);
        Double averageOperationRate = calculationService.calculateAverageOperationRate(hourlyOperationRates);
        
        // 4. 기존 통계 데이터 확인 (중복 방지)
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime startOfNextDay = targetDate.plusDays(1).atStartOfDay();
        Optional<Statistics> existingStatistics = statisticsRepository.getStatisticByCompanyIdAndDate(companyId, startOfDay, startOfNextDay);
        
        if (existingStatistics.isPresent()) {
            // 기존 데이터가 있으면 업데이트
            Statistics existing = existingStatistics.get();
            existing.updateStatistics(powerOnCount.intValue(), powerOnCount.doubleValue(), 
                totalDrivingTime, peakHour, lowHour, averageOperationRate);
            existing.updateHourlyRates(hourlyOperationRates);
            statisticsRepository.save(existing);
            log.info(StatisticConstants.LOG_STATISTICS_UPDATE_COMPLETE, companyId, targetDate);
        } else {
            // 기존 데이터가 없으면 신규 저장
            Statistics statistics = Statistics.builder()
                .company(company)
                .date(targetDate.atStartOfDay())
                .powerOnCount(powerOnCount.intValue())
                .averageDailyPowerCount(powerOnCount.doubleValue())
                .totalDrivingTime(totalDrivingTime)
                .peakHour(peakHour)
                .lowHour(lowHour)
                .averageOperationRate(averageOperationRate)
                .hour00(hourlyOperationRates[StatisticConstants.HOUR_00])
                .hour01(hourlyOperationRates[StatisticConstants.HOUR_01])
                .hour02(hourlyOperationRates[StatisticConstants.HOUR_02])
                .hour03(hourlyOperationRates[StatisticConstants.HOUR_03])
                .hour04(hourlyOperationRates[StatisticConstants.HOUR_04])
                .hour05(hourlyOperationRates[StatisticConstants.HOUR_05])
                .hour06(hourlyOperationRates[StatisticConstants.HOUR_06])
                .hour07(hourlyOperationRates[StatisticConstants.HOUR_07])
                .hour08(hourlyOperationRates[StatisticConstants.HOUR_08])
                .hour09(hourlyOperationRates[StatisticConstants.HOUR_09])
                .hour10(hourlyOperationRates[StatisticConstants.HOUR_10])
                .hour11(hourlyOperationRates[StatisticConstants.HOUR_11])
                .hour12(hourlyOperationRates[StatisticConstants.HOUR_12])
                .hour13(hourlyOperationRates[StatisticConstants.HOUR_13])
                .hour14(hourlyOperationRates[StatisticConstants.HOUR_14])
                .hour15(hourlyOperationRates[StatisticConstants.HOUR_15])
                .hour16(hourlyOperationRates[StatisticConstants.HOUR_16])
                .hour17(hourlyOperationRates[StatisticConstants.HOUR_17])
                .hour18(hourlyOperationRates[StatisticConstants.HOUR_18])
                .hour19(hourlyOperationRates[StatisticConstants.HOUR_19])
                .hour20(hourlyOperationRates[StatisticConstants.HOUR_20])
                .hour21(hourlyOperationRates[StatisticConstants.HOUR_21])
                .hour22(hourlyOperationRates[StatisticConstants.HOUR_22])
                .hour23(hourlyOperationRates[StatisticConstants.HOUR_23])
                .build();
            
            statisticsRepository.save(statistics);
            log.info(StatisticConstants.LOG_STATISTICS_SAVE_COMPLETE, companyId, targetDate);
        }
    }
} 