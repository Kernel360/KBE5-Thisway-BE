package org.thisway.triplog.dto.response;

import org.thisway.log.domain.GpsLogData;
import org.thisway.triplog.dto.CoordinatesInfo;

import java.time.LocalDateTime;
import java.util.List;

public record CurrentTripLogResponse (
        Integer angle,
        Integer speed,
        Integer totalTripMeter,
        LocalDateTime lastOccurredTime,
        List<CoordinatesInfo> coordinatesInfo
) {
    public static CurrentTripLogResponse from (GpsLogData lastGpsLogData, List<CoordinatesInfo> coordinatesInfo) {
        return new CurrentTripLogResponse(
                lastGpsLogData.angle(),
                lastGpsLogData.speed(),
                lastGpsLogData.totalTripMeter(),
                lastGpsLogData.occurredTime(),
                coordinatesInfo
        );
    }
}
