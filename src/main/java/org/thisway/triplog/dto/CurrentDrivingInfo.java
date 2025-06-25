package org.thisway.triplog.dto;

import org.thisway.log.domain.GpsLogData;
import org.thisway.triplog.entity.TripLog;

import java.time.LocalDateTime;
import java.util.Optional;

public record CurrentDrivingInfo(
        LocalDateTime startTime,
        Integer tripMeter,
        Integer speed,
        Double latitude,
        Double longitude
) {
    public static CurrentDrivingInfo from (TripLog tripLog, Optional<GpsLogData> gpsLogData) {

        if (gpsLogData.isPresent()) {
            return new CurrentDrivingInfo(
                    tripLog.getStartTime(),
                    gpsLogData.get().totalTripMeter() - tripLog.getTotalTripMeter(),
                    gpsLogData.get().speed(),
                    gpsLogData.get().latitude(),
                    gpsLogData.get().longitude()
            );
        } else {
            return new CurrentDrivingInfo(
                    tripLog.getStartTime(),
                    0,
                    0,
                    tripLog.getOnLatitude(),
                    tripLog.getOnLongitude()
            );
        }
    }
}
