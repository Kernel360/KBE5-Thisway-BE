package org.thisway.vehicle.vehicle_model.interfaces;

import org.thisway.vehicle.vehicle_model.domain.VehicleModel;

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
