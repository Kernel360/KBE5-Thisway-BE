package org.thisway.triplog.dto;

import org.thisway.log.domain.GpsLogData;

import java.time.LocalDateTime;

public record CurrentGpsLog (
        Double latitude,
        Double longitude,
        Integer angle,
        Integer speed,
        Integer totalTripMeter,
        LocalDateTime occurredTime
) {
    public static CurrentGpsLog from (GpsLogData gpsLogData) {
        return new CurrentGpsLog(
                gpsLogData.latitude(),
                gpsLogData.longitude(),
                gpsLogData.angle(),
                gpsLogData.speed(),
                gpsLogData.totalTripMeter(),
                gpsLogData.occurredTime()
        );
    }
}
