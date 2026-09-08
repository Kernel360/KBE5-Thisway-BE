package org.thisway.vehicle.log.interfaces;

import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.vehicle.log.domain.GpsStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Validate before any device lookup or write. Do not echo raw payloads in errors. */
public final class PowerLogRequestValidator {
    public static final int MAX_FUTURE_MINUTES = 5;
    private static final ZoneId DEVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);

    private PowerLogRequestValidator() {}

    public static void validate(PowerLogRequest request) {
        validate(request, Clock.systemUTC());
    }

    public static void validate(PowerLogRequest request, Clock clock) {
        try {
            require(request != null);
            text(request.mdn(), 20);
            text(request.tid(), 255);
            integer(request.mid(), Integer.MAX_VALUE);
            integer(request.pv(), Integer.MAX_VALUE);
            integer(request.did(), Integer.MAX_VALUE);
            LocalDateTime upper = LocalDateTime.ofInstant(clock.instant(), DEVICE_ZONE).plusMinutes(MAX_FUTURE_MINUTES);
            LocalDateTime on = time(request.onTime(), upper);
            if (request.offTime() != null && !request.offTime().isEmpty()) {
                require(!time(request.offTime(), upper).isBefore(on));
            }
            GpsStatus.fromCode(request.gcd());
            coordinate(request.lat(), 90_000_000);
            coordinate(request.lon(), 180_000_000);
            // Existing emulator Power payloads allow 0..365; heading is not used by this projection.
            integer(request.ang(), 365);
            integer(request.spd(), Integer.MAX_VALUE);
            integer(request.sum(), Integer.MAX_VALUE);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static LocalDateTime time(String value, LocalDateTime upper) {
        require(value != null && value.matches("[0-9]{14}"));
        LocalDateTime parsed = LocalDateTime.parse(value, TIME);
        require(parsed.getYear() >= 1000 && !parsed.isAfter(upper));
        return parsed;
    }

    private static void integer(String value, int max) {
        text(value, 10);
        require(value.matches("[0-9]+"));
        require(Integer.parseInt(value) <= max);
    }

    private static void coordinate(String value, long max) {
        text(value, 11);
        require(value.matches("-?[0-9]+"));
        require(Math.abs(Long.parseLong(value)) <= max);
    }

    private static void text(String value, int max) {
        require(value != null && !value.isBlank() && value.length() <= max);
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException();
    }
}
