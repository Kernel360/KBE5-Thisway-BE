package org.thisway.triplog.dto;

import org.thisway.log.domain.GpsLogData;
import org.thisway.log.dto.request.gpsLog.GpsLogEntry;

public record CoordinatesInfo(
        Long vehicleId,
        Double lat,
        Double lng
) {
    public static CoordinatesInfo from (GpsLogData gpsLogData) {
        return new CoordinatesInfo(
                gpsLogData.vehicleId(),
                gpsLogData.latitude(),
                gpsLogData.longitude()
        );
    }

    public static CoordinatesInfo fromGpsEntry (GpsLogEntry gpsLogEntry, Long vehicleId) {
        return new CoordinatesInfo(
                vehicleId,
                Double.parseDouble(gpsLogEntry.lat()) / 1_000_000.0,
                Double.parseDouble(gpsLogEntry.lon()) / 1_000_000.0
        );
    }
}
