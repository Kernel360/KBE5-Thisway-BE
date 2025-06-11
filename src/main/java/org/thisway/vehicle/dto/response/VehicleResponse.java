package org.thisway.vehicle.dto.response;

import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleModel;

public record VehicleResponse(

        Long id,
        String manufacturer,
        Integer modelYear,
        String model,
        String carNumber,
        String color,
        Integer mileage,
        boolean powerOn
) {

    public static VehicleResponse fromVehicle(Vehicle vehicle) {
        VehicleModel detail = vehicle.getVehicleModel();
        return new VehicleResponse(
                vehicle.getId(),
                detail.getManufacturer(),
                detail.getModelYear(),
                detail.getModel(),
                vehicle.getCarNumber(),
                vehicle.getColor(),
                vehicle.getMileage(),
                vehicle.isPowerOn()
        );
    }
}
