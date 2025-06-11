package org.thisway.vehicle.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.vehicle.dto.request.VehicleModelCreateRequest;
import org.thisway.vehicle.service.VehicleModelService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vehicle-models")
public class VehicleModelController {

    private final VehicleModelService vehicleModelService;

    @PostMapping
    public ResponseEntity<Void> registerVehicleModel(@RequestBody @Validated VehicleModelCreateRequest request) {
        vehicleModelService.registerVehicleModel(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .build();
    }
}
