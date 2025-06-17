package org.thisway.triplog.dto;

import java.time.LocalDateTime;

public record TripLogBriefInfo(
        Long vehicleId,
        String carNumber,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer tripMeter,
        Double latitude,
        Double longitude
) {

}
