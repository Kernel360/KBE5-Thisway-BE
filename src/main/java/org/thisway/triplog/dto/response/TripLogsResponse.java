package org.thisway.triplog.dto.response;

import org.springframework.data.domain.Page;
import org.thisway.triplog.dto.TripLogBriefInfo;
import org.thisway.triplog.entity.TripLog;

import java.util.List;

public record TripLogsResponse (
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
