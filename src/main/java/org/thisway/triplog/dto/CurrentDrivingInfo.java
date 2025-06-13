package org.thisway.triplog.dto;

import org.thisway.log.domain.GpsLogData;
import org.thisway.log.domain.PowerLogData;

import java.time.LocalDateTime;

public record CurrentDrivingInfo(
        LocalDateTime startTime,
        Integer tripMeter,
        Integer speed,
        Double latitude,
        Double longitude
) {
    public static CurrentDrivingInfo from (PowerLogData powerLogData, GpsLogData gpsLogData) {
        return new CurrentDrivingInfo(
                powerLogData.powerTime(),
                gpsLogData.totalTripMeter() - powerLogData.totalTripMeter(),
                gpsLogData.speed(),
                gpsLogData.latitude(),
                gpsLogData.longitude()
        );
    }
}
