package org.thisway.log.dto.request;

import java.time.LocalDateTime;

public record LogDataEntry(
        String gpsStatus, // "A", "V", "0" 중 하나
        Double latitude,
        Double longitude,
        Integer angle,
        Integer speed,
        Integer totalTripMeter,
        Integer batteryVoltage,
        LocalDateTime occurredTime,
        Integer secondOfMinute, // 0-59 사이의 초 값
        Long geofenceGroupId,
        Long geofenceId,
        Byte eventVal
) {}
