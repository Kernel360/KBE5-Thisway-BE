package org.thisway.vehicle.log.interfaces;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.thisway.support.common.*;
import static org.assertj.core.api.Assertions.*;

class TelemetryTimeValidationTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-07T03:00:00Z"), ZoneOffset.UTC);
    @Test void geofenceAllowsLateAndBoundaryButRejectsMalformedOrFutureTimes() {
        for (String time : List.of("20240229120000", "20260907120500")) GeofenceLogRequestValidator.validate(geofence(time, "1", "37000000"), clock);
        for (String time : List.of("20260230120000", "20260907120501", "202609071200", "09990907120000"))
            invalid(() -> GeofenceLogRequestValidator.validate(geofence(time, "1", "37000000"), clock));
        invalid(() -> GeofenceLogRequestValidator.validate(geofence("20260907120000", "3", "37000000"), clock));
        invalid(() -> GeofenceLogRequestValidator.validate(geofence("20260907120000", "1", "90000001"), clock));
        invalid(() -> GeofenceLogRequestValidator.validate(geofence("20260907120000", "1", "NaN"), clock));
    }
    @Test void gpsChecksNormalizedEntryTimeAsWellAsPacketBase() {
        GpsLogRequestValidator.validate(gps("20260907120000", "5", "0"), clock);
        GpsLogRequestValidator.validate(gps("20240229120000", "0", "0"), clock);
        invalid(() -> GpsLogRequestValidator.validate(gps("20260907120000", "5", "1"), clock));
        invalid(() -> GpsLogRequestValidator.validate(gps("20260907120600", "0", "0"), clock));
    }
    private GeofenceLogRequest geofence(String time, String event, String lat) {
        return new GeofenceLogRequest("fixture", "1", "1", "1", "1", time, "1", "1", event, "A", lat, "127000000", "90", "0", "1");
    }
    private GpsLogRequest gps(String time, String min, String sec) {
        return new GpsLogRequest("fixture", "1", "1", "1", "1", time, "1", List.of(new GpsLogEntry(min,sec,"A","37000000","127000000","90","0","1","12")));
    }
    private void invalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
