package org.thisway.vehicle.log.util;

import org.springframework.stereotype.Component;
import org.thisway.vehicle.log.domain.GpsStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class LogDataConverter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER_WITH_SEC = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public Double convertCoordinate(String coordinate) {
        double value = Double.parseDouble(coordinate);
        return value / 1_000_000.0;
    }

    public LocalDateTime convertDateTime(String dateTime) {
        return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER);
    }

    public LocalDateTime convertDateTimeWithSec(String dateTime) {
        if (dateTime == null || dateTime.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER_WITH_SEC);
    }

    public Integer convertToInteger(String value) {
        return Integer.parseInt(value);
    }

    public Long convertToLong(String value) {
        return Long.parseLong(value);
    }

    public Byte convertToByte(String value) {
        return Byte.parseByte(value);
    }

    public GpsStatus convertToGpsStatus(String code) {
        return GpsStatus.fromCode(code);
    }
}
