package org.thisway.triplog.dto.response;

import org.thisway.log.domain.PowerLogData;
import org.thisway.triplog.dto.CurrentGpsLog;
import org.thisway.vehicle.entity.Vehicle;

import java.time.LocalDateTime;
import java.util.List;

public record TripLogDetailResponse(
        String carNumber,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer tripMeter,
        Double avgSpeed,
        Double startLat,
        Double startLng,
        Double endLat,
        Double endLng,
        List<CurrentGpsLog> gpsLogs
) {
    public static TripLogDetailResponse from (
            Vehicle vehicle,
            PowerLogData powerOnLogs,
            PowerLogData powerOffLogs,
            List<CurrentGpsLog> gpsLogs,
            Double avgSpeed
    ) {
        return new TripLogDetailResponse(
                vehicle.getCarNumber(),
                powerOnLogs.powerTime(),
                powerOffLogs.powerTime(),
                powerOffLogs.totalTripMeter(),
                Math.round(avgSpeed * 100.0)/100.0,
                powerOnLogs.latitude(),
                powerOnLogs.longitude(),
                powerOffLogs.latitude(),
                powerOffLogs.longitude(),
                gpsLogs
        );
    }
}
