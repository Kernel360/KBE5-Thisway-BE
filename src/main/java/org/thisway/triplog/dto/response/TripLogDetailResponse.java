package org.thisway.triplog.dto.response;

import org.thisway.triplog.dto.CurrentGpsLog;
import org.thisway.triplog.entity.TripLog;
import org.thisway.vehicle.entity.Vehicle;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public record TripLogDetailResponse(
        String carNumber,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer tripMeter,
        Double avgSpeed,
        String onAddress,
        String offAddress,
        List<CurrentGpsLog> gpsLogs
) {
    public static TripLogDetailResponse from (
            Vehicle vehicle,
            TripLog tripLog,
            List<CurrentGpsLog> gpsLogs,
            Double avgSpeed
    ) {
        return new TripLogDetailResponse(
                vehicle.getCarNumber(),
                tripLog.getStartTime(),
                tripLog.getEndTime(),
                tripLog.getTotalTripMeter(),
                Math.round(avgSpeed * 100.0)/100.0,
                Optional.ofNullable(tripLog.getOnAddr()).orElse("") +
                        Optional.ofNullable(tripLog.getOnAddrDetail()).orElse(""),
                Optional.ofNullable(tripLog.getOffAddr()).orElse("") +
                        Optional.ofNullable(tripLog.getOffAddrDetail()).orElse(""),
                gpsLogs
        );
    }
}
