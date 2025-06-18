package org.thisway.log.service;

import org.springframework.stereotype.Service;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;

@Service
public interface GpsLogService {

    void saveGpsLog(GpsLogRequest request);
}
