package org.thisway.vehicle.triplog.interfaces;

import org.springframework.data.domain.Page;
import org.thisway.vehicle.triplog.domain.TripLog;
import org.thisway.vehicle.triplog.domain.TripLogBriefInfo;

import java.util.List;

public record TripLogsResponse(
        List<TripLogBriefInfo> tripLogs,
        int totalPages,
        long totalElements,
        int currentPage,
        int size
) {
    public static TripLogsResponse from(Page<TripLog> tripLogs) {
        return new TripLogsResponse(
                tripLogs.getContent().stream()
                        .map(TripLogBriefInfo::from)
                        .toList(),
                tripLogs.getTotalPages(),
                tripLogs.getTotalElements(),
                tripLogs.getNumber(),
                tripLogs.getSize()
        );
    }
}
