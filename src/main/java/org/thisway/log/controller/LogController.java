package org.thisway.log.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;
import org.thisway.log.dto.response.LogResponse;

public interface LogController {

    @PostMapping("/gps")
    ResponseEntity<LogResponse> receiveGpsLog(@RequestBody GpsLogRequest request);

    @PostMapping("/power")
    ResponseEntity<LogResponse> receivePowerLog(@RequestBody PowerLogRequest request);

    @PostMapping("/geofence")
    ResponseEntity<LogResponse> receiveGeofenceLog(@RequestBody GeofenceLogRequest request);
}
