package org.thisway.log.dto.request.gpsLog;

import java.util.List;

public record GpsLogRequest(
        String mdn,
        String tid,
        String mid,
        String pv,
        String did,
        String oTime,
        String cCnt,
        List<GpsLogEntry> cList
) {
}
