package org.thisway.vehicle.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.dto.request.VehicleSearchRequest;
import org.thisway.vehicle.dto.request.VehicleUpdateRequest;
import org.thisway.vehicle.dto.response.VehicleDashboardResponse;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.dto.response.VehicleTracksResponse;
import org.thisway.vehicle.dto.response.VehiclesResponse;
import org.thisway.vehicle.service.VehicleService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    public ResponseEntity<Void> registerVehicle(@RequestBody @Validated VehicleCreateRequest request) {

        vehicleService.registerVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getVehicleDetail(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(vehicleService.getVehicleDetail(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .build();
    }

    @GetMapping
    public ResponseEntity<VehiclesResponse> getVehicles(
            @PageableDefault Pageable pageable,
            @ModelAttribute VehicleSearchRequest searchRequest
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(vehicleService.getVehicles(searchRequest, pageable));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateVehicle(@PathVariable Long id,
                                              @RequestBody @Validated VehicleUpdateRequest request) {
        vehicleService.updateVehicle(id, request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .build();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<VehicleDashboardResponse> getVehicleDashboard() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(vehicleService.getVehicleDashboard());
    }

    @GetMapping("/track")
    public ResponseEntity<VehicleTracksResponse> getVehicleTracks(
            @PageableDefault Pageable pageable,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(vehicleService.getVehicleTracks(memberDetails.getCompanyId(), pageable));
    }
}
