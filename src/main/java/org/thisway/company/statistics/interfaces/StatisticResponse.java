package org.thisway.company.statistics.interfaces;

import org.thisway.company.statistics.StatisticConstants;
import org.thisway.company.statistics.domain.Statistics;
import org.thisway.vehicle.triplog.domain.TripLocationStats;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public record StatisticResponse(
        Long companyId,
        String date,
        Integer powerOnCount,
        Double averageDailyPowerCount,
        Integer totalDrivingTime,
        Integer peakHour,
        Double peakHourRate,
        Integer lowHour,
        Double lowHourRate,
        Double averageOperationRate,
        List<Double> hours,
        List<TripLocationStats> locationStats
) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static StatisticResponse from(Statistics statistics, List<TripLocationStats> locationStats) {
        // Statistics 엔티티의 getHourlyRatesArray() 메서드 활용
        List<Double> hours = Arrays.asList(statistics.getHourlyRatesArray());

        // peakHour와 lowHour의 실제 퍼센트 값 계산
        Double peakHourRate = getSafeHourlyRate(statistics.getPeakHour(), hours);
        Double lowHourRate = getSafeHourlyRate(statistics.getLowHour(), hours);

        return new StatisticResponse(
                statistics.getCompany().getId(),
                statistics.getDate().format(FORMATTER),
                statistics.getPowerOnCount(),
                statistics.getAverageDailyPowerCount(),
                statistics.getTotalDrivingTime(),
                statistics.getPeakHour(),
                peakHourRate,
                statistics.getLowHour(),
                lowHourRate,
                statistics.getAverageOperationRate(),
                hours,
                locationStats
        );
    }

    /**
     * 배열 기반 생성자 (집계된 통계용)
     */
    public static StatisticResponse fromAggregatedData(
            Long companyId, String dateRange, Integer powerOnCount, Double averageDailyPowerCount,
            Integer totalDrivingTime, Integer peakHour, Integer lowHour, Double averageOperationRate,
            List<Double> hourlyRates, List<TripLocationStats> locationStats) {

        Double peakHourRate = getSafeHourlyRate(peakHour, hourlyRates);
        Double lowHourRate = getSafeHourlyRate(lowHour, hourlyRates);

        return new StatisticResponse(
                companyId,
                dateRange,
                powerOnCount,
                averageDailyPowerCount,
                totalDrivingTime,
                peakHour,
                peakHourRate,
                lowHour,
                lowHourRate,
                averageOperationRate,
                hourlyRates,
                locationStats
        );
    }

    private static Double getSafeHourlyRate(Integer hour, List<Double> hourlyRates) {
        if (hour == null || hour < 0 || hour >= hourlyRates.size()) {
            return StatisticConstants.DEFAULT_HOURLY_RATE;
        }
        return hourlyRates.get(hour);
    }
}
