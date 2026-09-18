package org.thisway.vehicle.triplog.domain;

import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.log.interfaces.PowerLogRequest;
import org.thisway.vehicle.log.util.LogDataConverter;

import java.time.LocalDateTime;


public record TripLogSaveInput(
        Vehicle vehicle,
        String mdn,
        LocalDateTime onTime,
        LocalDateTime offTime,
        Double latitude,
        Double longitude,
        Integer totalTripMeter
) {
    public static TripLogSaveInput from(
            Vehicle vehicle,
            PowerLogRequest request,
            LogDataConverter converter
    ) {
        return new TripLogSaveInput(
                vehicle,
                request.mdn(),
                converter.convertDateTimeWithSec(request.onTime()),
                converter.convertDateTimeWithSec(request.offTime()),
                converter.convertCoordinate(request.lat()),
                converter.convertCoordinate(request.lon()),
                converter.convertToInteger(request.sum())
        );
    }
}
