package org.thisway.vehicle.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.thisway.common.ApiResponse;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.dto.request.VehicleUpdateRequest;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.dto.response.VehiclesResponse;
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

    @GetMapping("/{id}")
    public ApiResponse<VehicleResponse> getVehicleDetail(@PathVariable Long id){
        return ApiResponse.ok(vehicleService.getVehicleDetail(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteVehicle(@PathVariable Long id){
        vehicleService.deleteVehicle(id);
        return ApiResponse.noContent();
    }

    @GetMapping
    public ApiResponse<VehiclesResponse> getVehicles(@PageableDefault Pageable pageable) {
        return ApiResponse.ok(vehicleService.getVehicles(pageable));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> updateVehicle(@PathVariable Long id, @RequestBody @Validated VehicleUpdateRequest request) {
        vehicleService.updateVehicle(id, request);
        return ApiResponse.noContent();
    }
}
