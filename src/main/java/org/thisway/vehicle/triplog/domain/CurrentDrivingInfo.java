package org.thisway.vehicle.triplog.domain;

import org.thisway.vehicle.log.domain.GpsLogData;

import java.time.LocalDateTime;

public record CurrentDrivingInfo(
        LocalDateTime startTime,
        Integer tripMeter,
        Integer speed,
        Double latitude,
        Double longitude,
        Integer startOdometer
) {
    public static CurrentDrivingInfo from(TripLog tripLog, GpsLogData gps) {
        if (gps == null) {
            return new CurrentDrivingInfo(
                    tripLog.getStartTime(),
                    null,
                    0,
                    tripLog.getOnLatitude(),
                    tripLog.getOnLongitude(),
                    tripLog.getStartOdometer()
            );
        } else {
            return new CurrentDrivingInfo(
                    tripLog.getStartTime(),
                    TripLog.distanceFrom(tripLog.getStartOdometer(), gps.totalTripMeter()),
                    gps.speed(),
                    gps.latitude(),
                    gps.longitude(),
                    tripLog.getStartOdometer()
            );
        }
    }
}
