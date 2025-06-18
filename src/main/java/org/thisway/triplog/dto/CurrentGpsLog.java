package org.thisway.triplog.dto;

import org.thisway.log.domain.GpsLogData;

public record CurrentGpsLog (
        Double lat,
        Double lng
        //LocalDateTime occurredTime
) {
    public static CurrentGpsLog from (GpsLogData gpsLogData) {
        return new CurrentGpsLog(
                gpsLogData.latitude(),
                gpsLogData.longitude()
                //gpsLogData.occurredTime()
        );
    }
}
