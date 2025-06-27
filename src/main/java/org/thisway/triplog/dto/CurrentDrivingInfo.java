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
    public static CurrentDrivingInfo from (TripLog tripLog, GpsLogData gps) {
        if (gps == null) {
            return new CurrentDrivingInfo(
                    tripLog.getStartTime(),
                    0,
                    0,
                    tripLog.getOnLatitude(),
                    tripLog.getOnLongitude()
            );
        } else {
            return new CurrentDrivingInfo(
                    tripLog.getStartTime(),
                    gps.totalTripMeter() - tripLog.getTotalTripMeter(),
                    gps.speed(),
                    gps.latitude(),
                    gps.longitude()
            );
        }
    }
}
