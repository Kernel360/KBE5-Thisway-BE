package org.thisway.log.dto.request.gpsLog;

public record GpsLogEntry(
        String min,
        String sec,
        String gcd,
        String lat,
        String lon,
        String ang,
        String spd,
        String sum,
        String bat
) {
}
