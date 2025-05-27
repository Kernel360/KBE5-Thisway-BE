package org.thisway.log.dto.request.powerLog;

public record PowerLogRequest(
        String mdn,
        String tid,
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
