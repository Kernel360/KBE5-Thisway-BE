package org.thisway.vehicle.log.domain;

import java.time.LocalDateTime;

import org.thisway.vehicle.log.util.LogDataConverter;
import org.thisway.vehicle.log.interfaces.GpsLogEntry;

public record GpsLogData(
        Long vehicleId,
        String mdn,
        GpsStatus gpsStatus,
        Double latitude,
        Double longitude,
        Integer angle,
        Integer speed,
        Integer totalTripMeter,
        Integer batteryVoltage,
        LocalDateTime occurredTime
) {
    public static GpsLogData from(
            GpsLogEntry entry,
            String mdn,
            Long vehicleId,
            LocalDateTime occurredTime,
            LogDataConverter converter
    ) {
        return new GpsLogData(
                vehicleId,
                mdn,
                converter.convertToGpsStatus(entry.gcd()),
                converter.convertCoordinate(entry.lat()),
                converter.convertCoordinate(entry.lon()),
                converter.convertToInteger(entry.ang()),
                converter.convertToInteger(entry.spd()),
                converter.convertToInteger(entry.sum()),
                converter.convertToInteger(entry.bat()),
                occurredTime
        );
    }
}
