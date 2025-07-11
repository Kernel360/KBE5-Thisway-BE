package org.thisway.vehicle.triplog.interfaces;

import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.triplog.domain.TripLog;

import java.time.LocalDateTime;
import java.util.Optional;

public record TripLogDetailResponse(
        String carNumber,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer tripMeter,
        Double avgSpeed,
        String onAddress,
        String offAddress
) {
    public static TripLogDetailResponse from(
            Vehicle vehicle,
            TripLog tripLog,
            Double avgSpeed
    ) {
        return new TripLogDetailResponse(
                vehicle.getCarNumber(),
                tripLog.getStartTime(),
                tripLog.getEndTime(),
                tripLog.getTotalTripMeter(),
                Math.round(avgSpeed * 100.0) / 100.0,
                Optional.ofNullable(tripLog.getOnAddr()).orElse("") +
                        Optional.ofNullable(tripLog.getOnAddrDetail()).orElse(""),
                Optional.ofNullable(tripLog.getOffAddr()).orElse("") +
                        Optional.ofNullable(tripLog.getOffAddrDetail()).orElse("")
        );
    }
}
