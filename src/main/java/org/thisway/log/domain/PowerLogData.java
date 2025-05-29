package org.thisway.log.domain;

import java.time.LocalDateTime;
import org.thisway.log.converter.LogDataConverter;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;

public record PowerLogData(
        Long vehicleId,
        String mdn,
        boolean powerStatus,
        LocalDateTime powerTime,
        String gpsStatus,
        Double latitude,
        Double longitude,
        Integer totalTripMeter
) {
    public static PowerLogData from(
            PowerLogRequest request,
            String mdn,
            Long vehicleId,
            boolean powerStatus,
            String timeField,
            LogDataConverter converter
    ) {
        LocalDateTime powerTime = converter.convertDateTimeWithSec(timeField);

        return new PowerLogData(
                vehicleId,
                mdn,
                powerStatus,
                powerTime,
                request.gcd(),
                converter.convertCoordinate(request.lat()),
                converter.convertCoordinate(request.lon()),
                converter.convertToInteger(request.sum())
        );
    }
}
