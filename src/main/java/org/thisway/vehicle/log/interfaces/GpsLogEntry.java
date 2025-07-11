package org.thisway.vehicle.log.interfaces;

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
