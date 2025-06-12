package org.thisway.vehicle.dto.response;

import org.thisway.vehicle.entity.VehicleModel;

public record VehicleModelResponse(
        Long id,
        String manufacturer,
        Integer modelYear,
        String model
) {
    public static VehicleModelResponse from(VehicleModel vehicleModel) {
        return new VehicleModelResponse(
                vehicleModel.getId(),
                vehicleModel.getManufacturer(),
                vehicleModel.getModelYear(),
                vehicleModel.getName()
        );
    }
}
