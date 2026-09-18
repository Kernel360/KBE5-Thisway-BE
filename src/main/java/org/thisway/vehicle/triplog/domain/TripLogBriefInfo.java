package org.thisway.vehicle.triplog.domain;

import java.time.LocalDateTime;
import java.util.Optional;

public record TripLogBriefInfo(
        Long Id,
        Long vehicleId,
        String carNumber,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer tripMeter,
        String address
) {
    public static TripLogBriefInfo from(TripLog tripLog) {
        return new TripLogBriefInfo(
                tripLog.getId(),
                tripLog.getVehicle().getId(),
                tripLog.getVehicle().getCarNumber(),
                tripLog.getStartTime(),
                tripLog.getEndTime(),
                tripLog.getTotalTripMeter(),
                Optional.ofNullable(tripLog.getOnAddr()).orElse("") +
                        Optional.ofNullable(tripLog.getOnAddrDetail()).orElse("")
        );
    }
}
