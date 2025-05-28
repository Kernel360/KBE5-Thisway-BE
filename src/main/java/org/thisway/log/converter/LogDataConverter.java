package org.thisway.log.converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class LogDataConverter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("ccyyMMddHHmm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER_WITH_SEC = DateTimeFormatter.ofPattern("ccyyMMddHHmmss");

    public Double convertCoordinate(String coordinate) {
        double value = Double.parseDouble(coordinate);
        return value / 100000;
    }

    public LocalDateTime convertDateTime(String dateTime) {
        return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER);
    }

    public LocalDateTime convertDateTimeWithSec(String dateTime) {
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


}
