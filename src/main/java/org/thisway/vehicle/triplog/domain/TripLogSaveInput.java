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
        Integer odometer
) {
    public void validate() {
        if (vehicle == null || vehicle.getId() == null || onTime == null
                || (offTime != null && offTime.isBefore(onTime))
                || odometer == null || odometer < 0
                || latitude == null || !Double.isFinite(latitude) || Math.abs(latitude) > 90
                || longitude == null || !Double.isFinite(longitude) || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid trip observation");
        }
    }

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
