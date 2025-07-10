package org.thisway.vehicle.triplog.interfaces;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.security.utils.JwtTokenUtil;
import org.thisway.vehicle.triplog.application.StreamCoordinatesService;
import org.thisway.vehicle.triplog.application.TripLogService;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trip-log")
@Slf4j
public class TripLogController {

    private final TripLogService tripLogService;
    private final StreamCoordinatesService streamCoordinatesService;

    private final JwtTokenUtil jwtTokenUtil;

    @GetMapping("/{id}")
    public ResponseEntity<VehicleDetailResponse> getVehicleDetailTripLogs(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(tripLogService.getVehicleDetails(id));
    }

    @GetMapping("/current/{id}")
    public ResponseEntity<CurrentTripLogResponse> getCurrentGpsLogs(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(tripLogService.getCurrentGpsLogs(id, time));
    }

    @GetMapping(value = "/current/stream/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getVehicleCurrentGpsLogsSseEmitter(@PathVariable Long id, @RequestParam("token") String token) {
        if (!jwtTokenUtil.isValid(token)) {
            throw new CustomException(ErrorCode.AUTH_UNAUTHENTICATED);
        }
        return streamCoordinatesService.createStreamForVehicle(id, jwtTokenUtil.getUsernameFromToken(token));
    }

    @GetMapping
    public ResponseEntity<TripLogsResponse> getAllTripLogs(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @PageableDefault Pageable pageable
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(tripLogService.findTripLogs(memberDetails.getCompanyId(), pageable));
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<TripLogDetailResponse> getTripLogDetail(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(tripLogService.getTripLogDetails(id));
    }

    @GetMapping(value = "/detail/stream/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getTripLogsStream(@PathVariable Long id, @RequestParam("token") String token) {

        if (!jwtTokenUtil.isValid(token)) {
            throw new CustomException(ErrorCode.AUTH_UNAUTHENTICATED);
        }
        return streamCoordinatesService.createStreamForTripLog(id);
    }
}
