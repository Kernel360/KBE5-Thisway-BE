package org.thisway.vehicle.log.interfaces;

import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.vehicle.log.domain.GpsStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Shared HTTP and consumer-boundary validation. Never include raw telemetry in errors. */
public final class GpsLogRequestValidator {
    public static final int MAX_ENTRIES = 600;
    private static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("uuuuMMddHHmm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);

    private GpsLogRequestValidator() {}

    public static void validate(GpsLogRequest request) {
        try {
            require(request != null);
            text(request.mdn(), 20);
            text(request.tid(), 255);
            integer(request.mid(), 0, Integer.MAX_VALUE);
            integer(request.pv(), 0, Integer.MAX_VALUE);
            integer(request.did(), 0, Integer.MAX_VALUE);
            require(request.cList() != null && !request.cList().isEmpty()
                    && request.cList().size() <= MAX_ENTRIES);
            require(integer(request.cCnt(), 1, MAX_ENTRIES) == request.cList().size());
            require(request.oTime() != null && request.oTime().matches("[0-9]{12}([0-9]{2})?"));
            LocalDateTime.parse(request.oTime(), request.oTime().length() == 14 ? SECONDS : MINUTES);
            for (GpsLogEntry entry : request.cList()) {
                require(entry != null);
                optionalTime(entry.min());
                optionalTime(entry.sec());
                GpsStatus.fromCode(entry.gcd());
                coordinate(entry.lat(), 90_000_000);
                coordinate(entry.lon(), 180_000_000);
                integer(entry.ang(), 0, 359);
                integer(entry.spd(), 0, Integer.MAX_VALUE);
                integer(entry.sum(), 0, Integer.MAX_VALUE);
                integer(entry.bat(), 0, Integer.MAX_VALUE);
            }
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void text(String value, int max) {
        require(value != null && !value.isBlank() && value.length() <= max);
    }

    private static int integer(String value, int min, int max) {
        text(value, 10);
        require(value.matches("[0-9]+"));
        int number = Integer.parseInt(value);
        require(number >= min && number <= max);
        return number;
    }

    private static void coordinate(String value, double max) {
        text(value, 32);
        double number = Double.parseDouble(value);
        require(Double.isFinite(number) && Math.abs(number) <= max);
    }

    private static void optionalTime(String value) {
        if (value != null && !value.isEmpty()) integer(value, 0, 59);
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException();
    }
}
