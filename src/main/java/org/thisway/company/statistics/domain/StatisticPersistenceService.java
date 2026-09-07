package org.thisway.company.statistics.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
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
    private final StatisticsRevisionRecorder audit;
    private final StatisticsFleetSnapshots fleetSnapshots;

    /**
     * 통계 저장 (배치용)
     * - 하루 단위로 모든 통계를 미리 계산해서 DB에 저장
     * - 중복 방지: 같은 회사ID + 날짜 조합이 있으면 업데이트, 없으면 신규 저장
     */
    public void saveStatistics(Long companyId, LocalDate targetDate) {
        saveStatistics(companyId, targetDate, "DIRECT_OR_BATCH");
    }

    public void saveStatistics(Long companyId, LocalDate targetDate, String reason) {
        if (!java.util.Set.of("DIRECT_OR_BATCH", "LATE_TRIP_OBSERVED", "LATE_GPS_OBSERVED", "REVIEWED_FLEET_SNAPSHOT").contains(reason)) {
            throw new IllegalArgumentException("Unknown statistics calculation reason");
        }
        if (targetDate == null || targetDate.getYear() < 1000 || targetDate.getYear() > 9998
                || !targetDate.isBefore(LocalDate.now(KOREA_ZONE))) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        log.info("=== saveStatistics 호출 ===");

        // 1. 회사 정보 조회
        // Serialize both batch and direct correction before taking any aggregate snapshot.
        Company company = companyRepository.lockById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        // 2. 해당 날짜의 시작과 끝 시간 설정 (한국 시간대 기준)
        LocalDateTime startDateTime = targetDate.atStartOfDay();
        LocalDateTime endDateTime = targetDate.plusDays(1).atStartOfDay();

        log.info("계산된 시작 시간: {}, 종료 시간: {}", startDateTime, endDateTime);

        // Read the stored denominator before calculation. Automatic correction never silently changes it.
        Optional<Statistics> existingStatistics = statisticsRepository.getStatisticByCompanyIdAndDate(
                companyId, startDateTime, endDateTime);
        boolean capturedFleet = !fleetSnapshots.exists(companyId, targetDate);
        if (capturedFleet) {
            if (existingStatistics.isPresent() && !reason.equals("REVIEWED_FLEET_SNAPSHOT")) {
                throw new CustomException(ErrorCode.STATISTICS_FLEET_REVIEW_REQUIRED);
            }
            fleetSnapshots.captureCurrent(companyId, targetDate);
        }

        // 3. 통계 계산
        Long powerOnCount = calculationService.calculatePowerOnCount(companyId, startDateTime, endDateTime);
        var daily = calculationService.calculateDaily(companyId, startDateTime);
        Integer totalDrivingTime = daily.time().minutes();
        double[] hourlyOperationRates = daily.time().hourlyRates();
        Integer peakHour = calculationService.calculatePeakHourFromRates(hourlyOperationRates);
        Integer lowHour = calculationService.calculateLowHourFromRates(hourlyOperationRates);
        Double averageOperationRate = calculationService.calculateAverageOperationRate(hourlyOperationRates);

        if (existingStatistics.isPresent()) {
            // 기존 데이터가 있으면 업데이트
            Statistics existing = existingStatistics.get();
            if (!capturedFleet && sameValues(existing, powerOnCount, daily, peakHour, lowHour, averageOperationRate)) return;
            // Preserve the pre-audit row exactly, including legacy formulas, before its first correction.
            if (existing.getRevision() == 0) audit.append(existing, "PRE_AUDIT_SNAPSHOT");
            existing.updateStatistics(Math.toIntExact(powerOnCount), powerOnCount.doubleValue(),
                    totalDrivingTime, peakHour, lowHour, averageOperationRate);
            existing.updateHourlyRates(hourlyOperationRates);
            existing.markCalculated(daily.fleetSize(), daily.gpsObservations(), daily.unclosedTrips(),
                    LocalDateTime.now(KOREA_ZONE).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            statisticsRepository.save(existing);
            audit.append(existing, reason);
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
                    .powerOnCount(Math.toIntExact(powerOnCount))
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

            statistics.markCalculated(daily.fleetSize(), daily.gpsObservations(), daily.unclosedTrips(),
                    LocalDateTime.now(KOREA_ZONE).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            statisticsRepository.save(statistics);
            audit.append(statistics, reason);
            log.info("신규 통계 저장 완료: 회사 ID {}, 날짜 {}", companyId, targetDate);
        }
    }

    private static boolean sameValues(Statistics value, long powerOnCount, StatisticCalculationService.Daily daily,
                                      int peakHour, int lowHour, double averageRate) {
        return value.getFormulaVersion() == Statistics.CURRENT_FORMULA_VERSION
                && value.getFleetVehicleCount() == daily.fleetSize()
                && value.getGpsObservationCount() == daily.gpsObservations()
                && value.getUnclosedTripCount() == daily.unclosedTrips()
                && java.util.Objects.equals(value.getPowerOnCount(), Math.toIntExact(powerOnCount))
                && java.util.Objects.equals(value.getAverageDailyPowerCount(), (double) powerOnCount)
                && java.util.Objects.equals(value.getTotalDrivingTime(), daily.time().minutes())
                && java.util.Objects.equals(value.getPeakHour(), peakHour)
                && java.util.Objects.equals(value.getLowHour(), lowHour)
                && java.util.Objects.equals(value.getAverageOperationRate(), averageRate)
                && java.util.Arrays.equals(value.getHourlyRatesArray(),
                        java.util.Arrays.stream(daily.time().hourlyRates()).boxed().toArray(Double[]::new));
    }
}
