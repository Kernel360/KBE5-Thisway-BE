package org.thisway.triplog.dto;

import java.time.LocalDateTime;

public record TripLogBriefInfo(
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer tripMeter,
        Double latitude,
        Double longitude
) {

}
