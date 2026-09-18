package org.thisway.vehicle.log.application;

import org.springframework.stereotype.Service;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;

@Service
public interface GpsLogService {

    void saveGpsLog(GpsLogRequest request);
}
