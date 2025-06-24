package org.thisway.triplog.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.triplog.dto.response.CurrentTripLogResponse;
import org.thisway.triplog.dto.response.TripLogDetailResponse;
import org.thisway.triplog.dto.response.TripLogsResponse;
import org.thisway.triplog.dto.response.VehicleDetailResponse;
import org.thisway.triplog.service.TripLogService;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trip-log")
public class TripLogController {

    private final TripLogService tripLogService;

    @GetMapping("/{id}")
    public ResponseEntity<VehicleDetailResponse> getVehicleDetailTripLogs(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(tripLogService.getVehicleDetails(id));
    }

    @GetMapping("/current/{id}")
    public ResponseEntity<CurrentTripLogResponse> getCurrentTripLog(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(tripLogService.getCurrentGpsLogs(id, time));
    }

    @GetMapping()
    public ResponseEntity<TripLogsResponse> getAllTripLogs(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @PageableDefault Pageable pageable
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(tripLogService.findTripLogs(memberDetails.getCompanyId(), pageable));
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<TripLogDetailResponse> getTripLogDetail(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(tripLogService.getTripLogDetails(id, startTime, endTime));
    }
}
