package org.thisway.company.statistics.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.company.domain.Company;
import org.thisway.company.intrastructure.CompanyRepository;
import org.thisway.company.statistics.infrastructure.StatisticsRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StatisticPersistenceService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final StatisticsRepository statisticsRepository;
    private final CompanyRepository companyRepository;
    private final StatisticCalculationService calculationService;

    /**
     * 통계 저장 (배치용)
     * - 하루 단위로 모든 통계를 미리 계산해서 DB에 저장
     * - 중복 방지: 같은 회사ID + 날짜 조합이 있으면 업데이트, 없으면 신규 저장
     */
    public void saveStatistics(Long companyId, LocalDate targetDate) {
        log.info("=== saveStatistics 호출 ===");

        // 1. 회사 정보 조회
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        // 2. 해당 날짜의 시작과 끝 시간 설정 (한국 시간대 기준)
        LocalDateTime startDateTime = targetDate.atStartOfDay();
        LocalDateTime endDateTime = targetDate.atTime(23, 59, 59);

        log.info("계산된 시작 시간: {}, 종료 시간: {}", startDateTime, endDateTime);

        // 3. 통계 계산
        Long powerOnCount = calculationService.calculatePowerOnCount(companyId, startDateTime, endDateTime);
        Integer totalDrivingTime = calculationService.calculateTotalDrivingTime(companyId, startDateTime, endDateTime);
        double[] hourlyOperationRates = calculationService.calculateHourlyOperationRates(companyId, startDateTime, endDateTime);
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
            log.info("기존 통계 업데이트 완료: 회사 ID {}, 날짜 {}", companyId, targetDate);
        } else {
            // 기존 데이터가 없으면 신규 저장
            // 한국 시간대 기준으로 날짜 설정
            ZonedDateTime koreaDateTime = targetDate.atStartOfDay().atZone(KOREA_ZONE);
            LocalDateTime dateToSave = koreaDateTime.toLocalDateTime();
            log.info("저장할 날짜: {} (한국 시간대 기준)", dateToSave);

            Statistics statistics = Statistics.builder()
                    .company(company)
                    .date(dateToSave)
                    .powerOnCount(powerOnCount.intValue())
                    .averageDailyPowerCount(powerOnCount.doubleValue())
                    .totalDrivingTime(totalDrivingTime)
                    .peakHour(peakHour)
                    .lowHour(lowHour)
                    .averageOperationRate(averageOperationRate)
                    .hour00(hourlyOperationRates[0])
                    .hour01(hourlyOperationRates[1])
                    .hour02(hourlyOperationRates[2])
                    .hour03(hourlyOperationRates[3])
                    .hour04(hourlyOperationRates[4])
                    .hour05(hourlyOperationRates[5])
                    .hour06(hourlyOperationRates[6])
                    .hour07(hourlyOperationRates[7])
                    .hour08(hourlyOperationRates[8])
                    .hour09(hourlyOperationRates[9])
                    .hour10(hourlyOperationRates[10])
                    .hour11(hourlyOperationRates[11])
                    .hour12(hourlyOperationRates[12])
                    .hour13(hourlyOperationRates[13])
                    .hour14(hourlyOperationRates[14])
                    .hour15(hourlyOperationRates[15])
                    .hour16(hourlyOperationRates[16])
                    .hour17(hourlyOperationRates[17])
                    .hour18(hourlyOperationRates[18])
                    .hour19(hourlyOperationRates[19])
                    .hour20(hourlyOperationRates[20])
                    .hour21(hourlyOperationRates[21])
                    .hour22(hourlyOperationRates[22])
                    .hour23(hourlyOperationRates[23])
                    .build();

            statisticsRepository.save(statistics);
            log.info("신규 통계 저장 완료: 회사 ID {}, 날짜 {}", companyId, targetDate);
        }
    }
}
