package org.thisway.vehicle.dto.response;

import org.thisway.vehicle.entity.Vehicle;

public record VehicleDetailResponse(
        Long id,
        String manufacturer,
        Integer modelYear,
        String model,
        String carNumber,
        String color,
        Integer mileage,
        String companyName,
        String companyCrn
) {
    public static VehicleDetailResponse from(Vehicle vehicle) {
        return new VehicleDetailResponse(
                vehicle.getId(),
                vehicle.getVehicleDetail().getManufacturer(),
                vehicle.getVehicleDetail().getModelYear(),
                vehicle.getVehicleDetail().getModel(),
                vehicle.getCarNumber(),
                vehicle.getColor(),
                vehicle.getMileage(),
                vehicle.getCompany().getName(),
                vehicle.getCompany().getCrn()
        );
    }
} 