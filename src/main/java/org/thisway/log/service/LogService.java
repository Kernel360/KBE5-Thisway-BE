package org.thisway.log.service;

import org.thisway.log.domain.GpsLogData;
import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LogService {
    void savePowerLog(PowerLogRequest request);

    void saveGeofenceLog(GeofenceLogRequest request);

    List<GpsLogData> findGpsLogs(Long Id, LocalDateTime start, LocalDateTime end);

    Optional<GpsLogData> getCurrentGpsLog(Long Id, LocalDateTime start);
}
