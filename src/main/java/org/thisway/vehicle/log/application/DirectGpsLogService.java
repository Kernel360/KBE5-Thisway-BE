package org.thisway.vehicle.log.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gps-log-collect-mode", havingValue = "direct")
@Slf4j
public class DirectGpsLogService implements GpsLogService {

    private final GpsLogSaveService gpsLogSaveService;

    @Override
    public void saveGpsLog(GpsLogRequest request) {
        gpsLogSaveService.saveGpsLog(request);
    }
}
