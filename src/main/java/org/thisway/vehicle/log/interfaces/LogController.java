package org.thisway.vehicle.log.interfaces;

import org.springframework.web.bind.annotation.RequestHeader;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.common.CustomException;
import org.thisway.vehicle.log.application.DeviceTelemetryService;
import org.thisway.emulator.credential.DeviceIdentity;
import org.thisway.emulator.credential.DeviceAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.vehicle.log.application.GpsLogService;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final GpsLogService gpsLogService;
    private final DeviceAuthenticationService authentication;
    private final DeviceTelemetryService telemetry;
    private final org.thisway.vehicle.log.application.TelemetryRequestGuard requestGuard;

    @PostMapping("/gps")
    public ResponseEntity<LogResponse> receiveGpsLog(@RequestBody GpsLogRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Key", required = false) String key,
            @RequestHeader(value = "X-Request-Id", required = false) String nonce,
            @RequestHeader(value = "X-Request-Timestamp", required = false) String timestamp) {
        GpsLogRequestValidator.validate(request);
        gpsLogService.saveGpsLog(request, authenticate(deviceId, key, request.mdn(), nonce, timestamp));
        return ResponseEntity.status(HttpStatus.OK)
                .body(new LogResponse("000", "Success", request.mdn()));
    }

    @PostMapping("/power")
    public ResponseEntity<LogResponse> receivePowerLog(@RequestBody PowerLogRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Key", required = false) String key,
            @RequestHeader(value = "X-Request-Id", required = false) String nonce,
            @RequestHeader(value = "X-Request-Timestamp", required = false) String timestamp) {
        PowerLogRequestValidator.validate(request);
        telemetry.savePowerLog(request, authenticate(deviceId, key, request.mdn(), nonce, timestamp));
        return ResponseEntity.status(HttpStatus.OK)
                .body(new LogResponse("000", "Success", request.mdn()));
    }

    @PostMapping("/geofence")
    public ResponseEntity<LogResponse> receiveGeofenceLog(@RequestBody GeofenceLogRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Key", required = false) String key,
            @RequestHeader(value = "X-Request-Id", required = false) String nonce,
            @RequestHeader(value = "X-Request-Timestamp", required = false) String timestamp) {
        GeofenceLogRequestValidator.validate(request);
        telemetry.saveGeofenceLog(request, authenticate(deviceId, key, request.mdn(), nonce, timestamp));
        return ResponseEntity.status(HttpStatus.OK)
                .body(new LogResponse("000", "Success", request.mdn()));
    }
    private DeviceIdentity authenticate(String id, String key, String mdn, String nonce, String timestamp) {
        if (id == null || id.length() > 19 || !id.matches("[1-9][0-9]*")) throw denied();
        try {
            var identity = authentication.authenticate(Long.parseLong(id), key, mdn);
            requestGuard.accept(identity.emulatorId(), nonce, timestamp);
            return identity;
        } catch (NumberFormatException invalid) {
            throw denied();
        }
    }

    private static CustomException denied() {
        return new CustomException(ErrorCode.DEVICE_AUTHENTICATION_FAILED);
    }
}
