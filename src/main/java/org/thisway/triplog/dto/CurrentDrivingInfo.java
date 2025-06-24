package org.thisway.triplog.dto;

import org.thisway.log.domain.GpsLogData;
import org.thisway.triplog.entity.TripLog;

import java.time.LocalDateTime;

public record CurrentDrivingInfo(
        LocalDateTime startTime,
        Integer tripMeter,
        Integer speed,
        Double latitude,
        Double longitude
) {
    public static CurrentDrivingInfo from (TripLog tripLog, GpsLogData gpsLogData) {
        return new CurrentDrivingInfo(
                tripLog.getStartTime(),
                gpsLogData.totalTripMeter() - tripLog.getTotalTripMeter(),
                gpsLogData.speed(),
                gpsLogData.latitude(),
                gpsLogData.longitude()
        );
    }
}
