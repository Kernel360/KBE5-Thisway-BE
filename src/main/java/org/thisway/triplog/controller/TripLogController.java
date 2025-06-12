package org.thisway.triplog.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.thisway.triplog.dto.response.VehicleDetailResponse;
import org.thisway.triplog.service.TripLogService;

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
}
