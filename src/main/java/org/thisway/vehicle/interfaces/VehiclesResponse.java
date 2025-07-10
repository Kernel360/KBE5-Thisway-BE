package org.thisway.vehicle.interfaces;

import org.springframework.data.domain.Page;
import org.thisway.vehicle.domain.Vehicle;

import java.util.List;

public record VehiclesResponse(
        List<VehicleResponse> vehicles,
        int totalPages,
        long totalElements,
        int currentPage,
        int size
) {
    public static VehiclesResponse from(Page<Vehicle> page) {
        return new VehiclesResponse(
                page.getContent().stream()
                        .map(VehicleResponse::fromVehicle)
                        .toList(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize()
        );
    }
} 
