package org.thisway.vehicle.triplog.domain;

import org.thisway.vehicle.log.domain.GpsLogData;
import org.thisway.vehicle.log.interfaces.GpsLogEntry;

public record CoordinatesInfo(
        Long vehicleId,
        Double lat,
        Double lng,
        Integer angle
) {
    public static CoordinatesInfo from(GpsLogData gpsLogData) {
        return new CoordinatesInfo(
                gpsLogData.vehicleId(),
                gpsLogData.latitude(),
                gpsLogData.longitude(),
                gpsLogData.angle()
        );
    }

    public static CoordinatesInfo from(GpsLogEntry gpsLogEntry, Long vehicleId) {
        return new CoordinatesInfo(
                vehicleId,
                Double.parseDouble(gpsLogEntry.lat()) / 1_000_000.0,
                Double.parseDouble(gpsLogEntry.lon()) / 1_000_000.0,
                Integer.parseInt(gpsLogEntry.ang())
        );
    }
}
