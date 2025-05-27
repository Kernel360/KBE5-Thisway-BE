package org.thisway.log.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.common.ApiResponse;
import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;
import org.thisway.log.dto.response.LogResponse;
import org.thisway.log.service.LogService;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @PostMapping("/gps")
    public ApiResponse<LogResponse> receiveGpsLog(@RequestBody GpsLogRequest request) {
        logService.saveGpsLog(request);
        return ApiResponse.ok(new LogResponse("000", "Success", request.mdn()));
    }

    @PostMapping("/power")
    public ApiResponse<LogResponse> receivePowerLog(@RequestBody PowerLogRequest request) {
        logService.savePowerLog(request);
        return ApiResponse.ok(new LogResponse("000", "Success", request.mdn()));
    }

    @PostMapping("/geofence")
    public ApiResponse<LogResponse> receiveGeofenceLog(@RequestBody GeofenceLogRequest request) {
        logService.saveGeofenceLog(request);
        return ApiResponse.ok(new LogResponse("000", "Success", request.mdn()));
    }
}
