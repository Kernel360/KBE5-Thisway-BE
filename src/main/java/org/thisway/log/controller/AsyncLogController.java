package org.thisway.log.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;
import org.thisway.log.dto.response.LogResponse;
import org.thisway.log.producer.GpsLogProducer;
import org.thisway.log.service.LogService;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "log.mode", havingValue = "async")
public class AsyncLogController implements LogController {

    private final LogService logService;
    private final GpsLogProducer gpsLogProducer;

    @Override
    @PostMapping("/gps")
    public ResponseEntity<LogResponse> receiveGpsLog(GpsLogRequest request) {
        gpsLogProducer.sendGpsLog(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new LogResponse("000", "Success", request.mdn()));
    }

    @Override
    @PostMapping("/power")
    public ResponseEntity<LogResponse> receivePowerLog(PowerLogRequest request) {
        logService.savePowerLog(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new LogResponse("000", "Success", request.mdn()));
    }

    @Override
    @PostMapping("/geofence")
    public ResponseEntity<LogResponse> receiveGeofenceLog(GeofenceLogRequest request) {
        logService.saveGeofenceLog(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new LogResponse("000", "Success", request.mdn()));
    }
}
