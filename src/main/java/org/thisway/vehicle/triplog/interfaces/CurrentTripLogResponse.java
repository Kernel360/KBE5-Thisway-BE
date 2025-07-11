package org.thisway.vehicle.triplog.interfaces;

import org.thisway.vehicle.log.domain.GpsLogData;
import org.thisway.vehicle.log.interfaces.GpsLogEntry;
import org.thisway.vehicle.triplog.domain.CoordinatesInfo;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public record CurrentTripLogResponse(
        Integer angle,
        Integer speed,
        Integer totalTripMeter,
        //LocalDateTime lastOccurredTime,
        List<CoordinatesInfo> coordinatesInfo
) {
    public static CurrentTripLogResponse from(List<GpsLogData> gpsLogs) {
        GpsLogData lastGpsLog = gpsLogs.getLast();
        return new CurrentTripLogResponse(
                lastGpsLog.angle(),
                lastGpsLog.speed(),
                lastGpsLog.totalTripMeter(),
                //lastGpsLog.occurredTime(),
                gpsLogs.stream()
                        .map(CoordinatesInfo::from)
                        .toList()
        );
    }

    public static CurrentTripLogResponse from(List<GpsLogEntry> gpsLogEntries, Long vehicleId) {
        return new CurrentTripLogResponse(
                Integer.parseInt(gpsLogEntries.getLast().ang()),
                Integer.parseInt(gpsLogEntries.getLast().spd()),
                Integer.parseInt(gpsLogEntries.getLast().sum()),
                gpsLogEntries.stream()
                        .sorted(
                                Comparator.comparing((GpsLogEntry e) -> Integer.parseInt(e.min()))
                                        .thenComparing(e -> Integer.parseInt(e.sec()))
                        )
                        .map(entry -> CoordinatesInfo.from(entry, vehicleId))
                        .collect(Collectors.toList())
        );
    }
}
