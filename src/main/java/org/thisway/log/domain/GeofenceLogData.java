package org.thisway.log.domain;

import java.time.LocalDateTime;
import org.thisway.log.converter.LogDataConverter;
import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;

public record GeofenceLogData(
        Long vehicleId,
        String mdn,
        LocalDateTime occurredTime,
        Long geofenceGroupId,
        Long geofenceId,
        Byte eventVal,
        String gpsStatus,
        Double latitude,
        Double longitude,
        Integer angle
) {
    public static GeofenceLogData from(
            GeofenceLogRequest request,
            String mdn,
            Long vehicleId,
            LogDataConverter converter
    ) {
        return new GeofenceLogData(
                vehicleId,
                mdn,
                converter.convertDateTimeWithSec(request.oTime()),
                converter.convertToLong(request.geoGrpId()),
                converter.convertToLong(request.geoPId()),
                converter.convertToByte(request.evtVal()),
                request.gcd(),
                converter.convertCoordinate(request.lat()),
                converter.convertCoordinate(request.lon()),
                converter.convertToInteger(request.ang())
        );
    }
}
