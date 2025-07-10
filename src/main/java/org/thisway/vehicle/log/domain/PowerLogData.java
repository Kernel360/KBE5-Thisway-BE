package org.thisway.vehicle.log.domain;

import java.time.LocalDateTime;

import org.thisway.vehicle.log.util.LogDataConverter;
import org.thisway.vehicle.log.interfaces.PowerLogRequest;

public record PowerLogData(
        Long vehicleId,
        String mdn,
        boolean powerStatus,
        LocalDateTime powerTime,
        GpsStatus gpsStatus,
        Double latitude,
        Double longitude,
        Integer totalTripMeter
) {
    public static PowerLogData from(
            PowerLogRequest request,
            Long vehicleId,
            boolean powerStatus,
            String timeField,
            LogDataConverter converter
    ) {
        LocalDateTime powerTime = converter.convertDateTimeWithSec(timeField);

        return new PowerLogData(
                vehicleId,
                request.mdn(),
                powerStatus,
                powerTime,
                converter.convertToGpsStatus(request.gcd()),
                converter.convertCoordinate(request.lat()),
                converter.convertCoordinate(request.lon()),
                converter.convertToInteger(request.sum())
        );
    }
}
