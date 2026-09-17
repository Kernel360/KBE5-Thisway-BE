package org.thisway.company.statistics.domain;

import org.thisway.vehicle.triplog.domain.TripLocationRaw;
import java.time.LocalDateTime;
import java.util.List;

public interface StatisticsSourceReader {
    List<CompletedTripTime.Interval> completed(long companyId, LocalDateTime start, LocalDateTime end);
    long starts(long companyId, LocalDateTime start, LocalDateTime end);
    long gps(long companyId, LocalDateTime start, LocalDateTime end);
    long unclosed(long companyId, LocalDateTime start, LocalDateTime end);
    List<TripLocationRaw> locations(long companyId, LocalDateTime start, LocalDateTime end);
}
