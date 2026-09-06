package org.thisway.company.statistics.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.company.statistics.StatisticConstants;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.log.infrastructure.LogRepository;
import org.thisway.vehicle.triplog.infrastructure.TripLogRepository;
import java.time.LocalDateTime;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticCalculationService {
    private final TripLogRepository tripLogRepository;
    private final VehicleRepository vehicleRepository;
    private final LogRepository logRepository;

    public record Daily(CompletedTripTime.Result time, long fleetSize, long gpsObservations, long unclosedTrips) { }

    public Daily calculateDaily(Long companyId, LocalDateTime start) {
        long fleet = vehicleRepository.countByCompanyIdAndActiveTrue(companyId);
        var end = start.plusDays(1);
        var intervals = tripLogRepository.findCompletedOverlapping(companyId, start, end).stream()
                .map(t -> new CompletedTripTime.Interval(t.getVehicle().getId(), t.getStartTime(), t.getEndTime()))
                .toList();
        return new Daily(CompletedTripTime.calculate(intervals, start, fleet), fleet,
                logRepository.countGpsObservations(companyId, start, end),
                tripLogRepository.countUnclosedBefore(companyId, end));
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
        return tripLogRepository.countDistinctStarts(companyId, start, end);
    }
}
