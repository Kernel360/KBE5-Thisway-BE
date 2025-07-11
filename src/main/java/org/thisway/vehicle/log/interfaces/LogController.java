package org.thisway.vehicle.log.interfaces;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.vehicle.log.application.GpsLogService;
import org.thisway.vehicle.log.application.LogService;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;
    private final GpsLogService gpsLogService;

    @PostMapping("/gps")
    public ResponseEntity<LogResponse> receiveGpsLog(@RequestBody GpsLogRequest request) {
        gpsLogService.saveGpsLog(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new LogResponse("000", "Success", request.mdn()));
    }

    @PostMapping("/power")
    public ResponseEntity<LogResponse> receivePowerLog(@RequestBody PowerLogRequest request) {
        logService.savePowerLog(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new LogResponse("000", "Success", request.mdn()));
    }

    @PostMapping("/geofence")
    public ResponseEntity<LogResponse> receiveGeofenceLog(@RequestBody GeofenceLogRequest request) {
        logService.saveGeofenceLog(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new LogResponse("000", "Success", request.mdn()));
    }
}
