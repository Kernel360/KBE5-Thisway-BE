package org.thisway.triplog.dto;

import org.thisway.triplog.entity.TripLog;

import java.time.LocalDateTime;
import java.util.Optional;

public record TripLogBriefInfo(
        Long vehicleId,
        String carNumber,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer tripMeter,
        String address
) {
    public static TripLogBriefInfo from(TripLog tripLog) {
        return new TripLogBriefInfo(
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
