package org.thisway.vehicle.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.common.ApiResponse;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.service.VehicleService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    public ApiResponse<Void> registerVehicle(@RequestBody @Validated VehicleCreateRequest request) {

        vehicleService.registerVehicle(request);
        return ApiResponse.created();
    }
}
