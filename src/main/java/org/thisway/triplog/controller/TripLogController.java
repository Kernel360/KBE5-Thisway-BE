package org.thisway.triplog.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.thisway.triplog.dto.CurrentGpsLog;
import org.thisway.triplog.dto.response.VehicleDetailResponse;
import org.thisway.triplog.service.TripLogService;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/trip-log")
public class TripLogController {

    private final TripLogService tripLogService;

    @GetMapping("/{id}")
    public ResponseEntity<VehicleDetailResponse> vehicleDetailTripLogs(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(tripLogService.getVehicleDetails(id));
    }

    @GetMapping("/current/{id}")
    public ResponseEntity<List<CurrentGpsLog>> TripLog(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(tripLogService.getCurrentGpsLogs(id, time));
    }
}
