package org.thisway.triplog.dto;

public record TripLocationStats(
        String addr,
        Long count,
        Double rate
) {
    public static TripLocationStats from(TripLocationRaw tripLocationRaw, Long total) {
        double rate = total > 0 ? tripLocationRaw.count() * 100.0 / total : 0.0;
        return new TripLocationStats(tripLocationRaw.addr(), tripLocationRaw.count(), Math.round(rate * 100.0) / 100.0);
    }
}
