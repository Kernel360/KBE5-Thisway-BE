package org.thisway.triplog.dto;

import java.time.LocalDateTime;

public record TripLogBriefInfo(
        Long vehicle_id,
        String vehicle_name,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer tripMeter,
        Double latitude,
        Double longitude
) {

}
