package org.thisway.company.statistics.application;

import java.time.LocalDate;

/** Publish synchronously inside the source transaction; dates are inclusive, in Asia/Seoul. */
public record StatisticsSourceChanged(long companyId, LocalDate fromDate, LocalDate throughDate, String reason) {
    public StatisticsSourceChanged {
        if (companyId <= 0 || fromDate == null || throughDate == null
                || !("TRIP_OBSERVED".equals(reason) || "GPS_OBSERVED".equals(reason))) {
            throw new IllegalArgumentException("Invalid statistics source change");
        }
    }
}
