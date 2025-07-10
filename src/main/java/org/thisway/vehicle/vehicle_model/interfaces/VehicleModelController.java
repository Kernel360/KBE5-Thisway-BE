package org.thisway.vehicle.vehicle_model.interfaces;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.vehicle.vehicle_model.application.VehicleModelService;

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

    @GetMapping
    public ResponseEntity<VehicleModelsResponse> getVehicleModels(
            @PageableDefault Pageable pageable) {
        VehicleModelsResponse response = vehicleModelService.getVehicleModels(pageable);
        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }
}
