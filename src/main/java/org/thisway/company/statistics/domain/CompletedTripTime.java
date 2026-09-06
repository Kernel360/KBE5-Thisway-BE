package org.thisway.company.statistics.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Half-open, per-vehicle interval union. Engine-on time, not time physically moving. */
public final class CompletedTripTime {
    private CompletedTripTime() { }
    public record Interval(long vehicleId, LocalDateTime start, LocalDateTime end) { }
    public record Result(int minutes, double[] hourlyRates) { }

    public static Result calculate(List<Interval> trips, LocalDateTime dayStart, long vehicleCount) {
        if (vehicleCount < 0 || !dayStart.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            throw new IllegalArgumentException("A day boundary and non-negative fleet size are required");
        }
        var dayEnd = dayStart.plusDays(1);
        Map<Long, List<Interval>> byVehicle = new HashMap<>();
        for (var trip : trips) {
            if (trip.start() == null || trip.end() == null || !trip.end().isAfter(trip.start())) continue;
            var start = trip.start().isBefore(dayStart) ? dayStart : trip.start();
            var end = trip.end().isAfter(dayEnd) ? dayEnd : trip.end();
            if (end.isAfter(start)) byVehicle.computeIfAbsent(trip.vehicleId(), key -> new ArrayList<>())
                    .add(new Interval(trip.vehicleId(), start, end));
        }
        if (byVehicle.size() > vehicleCount) throw new IllegalArgumentException("Intervals exceed fleet size");
        long[] nanos = new long[24];
        for (var intervals : byVehicle.values()) {
            intervals.sort(Comparator.comparing(Interval::start).thenComparing(Interval::end));
            LocalDateTime start = intervals.getFirst().start();
            LocalDateTime end = intervals.getFirst().end();
            for (int i = 1; i < intervals.size(); i++) {
                var next = intervals.get(i);
                if (!next.start().isAfter(end)) {
                    if (next.end().isAfter(end)) end = next.end();
                } else {
                    add(nanos, dayStart, start, end);
                    start = next.start(); end = next.end();
                }
            }
            add(nanos, dayStart, start, end);
        }
        double[] rates = new double[24];
        long total = 0;
        for (int hour = 0; hour < 24; hour++) {
            total = Math.addExact(total, nanos[hour]);
            rates[hour] = vehicleCount == 0 ? 0 : nanos[hour] / (3_600_000_000_000.0 * vehicleCount) * 100;
        }
        return new Result(Math.toIntExact(total / 60_000_000_000L), rates);
    }

    private static void add(long[] nanos, LocalDateTime dayStart, LocalDateTime start, LocalDateTime end) {
        for (int hour = 0; hour < 24; hour++) {
            var from = dayStart.plusHours(hour);
            var to = from.plusHours(1);
            var clippedStart = start.isAfter(from) ? start : from;
            var clippedEnd = end.isBefore(to) ? end : to;
            if (clippedEnd.isAfter(clippedStart)) nanos[hour] = Math.addExact(nanos[hour],
                    Duration.between(clippedStart, clippedEnd).toNanos());
        }
    }
}
