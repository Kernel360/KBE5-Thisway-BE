package org.thisway.vehicle.log.interfaces;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.vehicle.log.domain.GpsStatus;

public final class GeofenceLogRequestValidator {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
    private GeofenceLogRequestValidator() {}
    public static void validate(GeofenceLogRequest request) { validate(request, Clock.systemUTC()); }
    public static void validate(GeofenceLogRequest request, Clock clock) {
        try {
            require(request != null);
            text(request.mdn(), 20); text(request.tid(), 255);
            number(request.mid(), Integer.MAX_VALUE); number(request.pv(), Integer.MAX_VALUE); number(request.did(), Integer.MAX_VALUE);
            require(request.oTime() != null && request.oTime().matches("[0-9]{14}"));
            var time = LocalDateTime.parse(request.oTime(), TIME);
            require(time.getYear() >= 1000 && !time.isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")).plusMinutes(5)));
            number(request.geoGrpId(), Long.MAX_VALUE); number(request.geoPId(), Long.MAX_VALUE);
            require("1".equals(request.evtVal()) || "2".equals(request.evtVal()));
            GpsStatus.fromCode(request.gcd());
            coordinate(request.lat(), 90_000_000); coordinate(request.lon(), 180_000_000);
            number(request.ang(), 359); number(request.spd(), Integer.MAX_VALUE); number(request.sum(), Integer.MAX_VALUE);
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
    private static void number(String value, long max) {
        text(value, 19); require(value.matches("[0-9]+")); require(Long.parseLong(value) <= max);
    }
    private static void coordinate(String value, long max) {
        text(value, 11); require(value.matches("-?[0-9]+")); require(Math.abs(Long.parseLong(value)) <= max);
    }
    private static void text(String value, int max) { require(value != null && !value.isBlank() && value.length() <= max); }
    private static void require(boolean condition) { if (!condition) throw new IllegalArgumentException(); }
}
