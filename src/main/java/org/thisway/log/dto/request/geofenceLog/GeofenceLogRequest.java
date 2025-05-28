package org.thisway.log.dto.request.geofenceLog;

public record GeofenceLogRequest(
        String mdn,
        String tid,
        String mid,
        String pv,
        String did,
        String oTime,
        String geoGrpId,
        String geoPId,
        String evtVal,
        String gcd,
        String lat,
        String lon,
        String ang,
        String spd,
        String sum
) {
}
