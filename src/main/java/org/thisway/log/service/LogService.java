package org.thisway.log.service;

import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;

public interface LogService {
    void savePowerLog(PowerLogRequest request);

    void saveGeofenceLog(GeofenceLogRequest request);
}
