package org.thisway.vehicle.log.interfaces;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PowerLogRequest(
        String mdn,
        String tid,
        String mid,
        String pv,
        String did,
        String onTime,
        String offTime,
        String gcd,
        String lat,
        String lon,
        String ang,
        String spd,
        String sum
) {
}
