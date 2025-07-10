package org.thisway.vehicle.log.domain;

import java.time.LocalDateTime;

import org.thisway.vehicle.log.util.LogDataConverter;
import org.thisway.vehicle.log.interfaces.GeofenceLogRequest;

public record GeofenceLogData(
        Long vehicleId,
        String mdn,
        LocalDateTime occurredTime,
        Long geofenceGroupId,
        Long geofenceId,
        Byte eventVal,
        GpsStatus gpsStatus,
        Double latitude,
        Double longitude,
        Integer angle
) {
    public static GeofenceLogData from(
            GeofenceLogRequest request,
            Long vehicleId,
            LogDataConverter converter
    ) {
        return new GeofenceLogData(
                vehicleId,
                request.mdn(),
                converter.convertDateTimeWithSec(request.oTime()),
                converter.convertToLong(request.geoGrpId()),
                converter.convertToLong(request.geoPId()),
                converter.convertToByte(request.evtVal()),
                converter.convertToGpsStatus(request.gcd()),
                converter.convertCoordinate(request.lat()),
                converter.convertCoordinate(request.lon()),
                converter.convertToInteger(request.ang())
        );
    }
}
