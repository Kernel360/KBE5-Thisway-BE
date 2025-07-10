package org.thisway.vehicle.log.application;

import org.thisway.vehicle.log.domain.GpsLogData;
import org.thisway.vehicle.log.interfaces.GeofenceLogRequest;
import org.thisway.vehicle.log.interfaces.PowerLogRequest;

import java.time.LocalDateTime;
import java.util.List;

public interface LogService {
    void savePowerLog(PowerLogRequest request);

    void saveGeofenceLog(GeofenceLogRequest request);

    List<GpsLogData> findGpsLogs(Long Id, LocalDateTime start, LocalDateTime end);

    GpsLogData getCurrentGpsLog(Long Id, LocalDateTime start);
}
