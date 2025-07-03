package org.thisway.triplog.dto;

import org.thisway.log.domain.GpsLogData;

public record CoordinatesInfo(
        Double lat,
        Double lng
        //LocalDateTime occurredTime
) {
    public static CoordinatesInfo from (GpsLogData gpsLogData) {
        return new CoordinatesInfo(
                gpsLogData.latitude(),
                gpsLogData.longitude()
                //gpsLogData.occurredTime()
        );
    }
}
