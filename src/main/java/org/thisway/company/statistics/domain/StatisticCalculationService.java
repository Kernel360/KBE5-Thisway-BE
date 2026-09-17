package org.thisway.company.statistics.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.company.statistics.StatisticConstants;
import java.time.LocalDateTime;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticCalculationService {
    private final StatisticsFleetSnapshots fleetSnapshots;
    private final StatisticsSourceReader sources;

    public record Daily(CompletedTripTime.Result time, long fleetSize, long gpsObservations, long unclosedTrips) { }

    public Daily calculateDaily(Long companyId, LocalDateTime start) {
        long fleet = fleetSnapshots.size(companyId, start.toLocalDate());
        var end = start.plusDays(1);
        var intervals = sources.completed(companyId, start, end);
        return new Daily(CompletedTripTime.calculate(intervals, start, fleet), fleet,
                sources.gps(companyId, start, end), sources.unclosed(companyId, start, end));
    }

    public Integer calculatePeakHourFromRates(double[] rates) {
        int result = StatisticConstants.DEFAULT_PEAK_HOUR;
        double max = 0;
        for (int hour = 0; hour < 24; hour++) {
            if (rates[hour] > max) { max = rates[hour]; result = hour; }
        }
        return result;
    }

    public Integer calculateLowHourFromRates(double[] rates) {
        int result = StatisticConstants.DEFAULT_LOW_HOUR;
        double min = Double.POSITIVE_INFINITY;
        for (int hour = 0; hour < 24; hour++) {
            if (rates[hour] > 0 && rates[hour] < min) { min = rates[hour]; result = hour; }
        }
        return result;
    }

    public Double calculateAverageOperationRate(double[] rates) {
        return Arrays.stream(rates).average().orElse(0);
    }

    public Long calculatePowerOnCount(Long companyId, LocalDateTime start, LocalDateTime end) {
        return sources.starts(companyId, start, end);
    }
}
