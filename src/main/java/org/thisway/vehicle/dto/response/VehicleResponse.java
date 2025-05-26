package org.thisway.vehicle.dto.response;

import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleDetail;

public record VehicleResponse(

        String manufacturer,
        Integer modelYear,
        String model,
        Long companyId,
        String companyName,
        String carNumber,
        Integer mileage
) {

    public static VehicleResponse fromVehicle(Vehicle vehicle) {
        VehicleDetail detail = vehicle.getVehicleDetail();
        return new VehicleResponse(
                detail.getManufacturer(),
                detail.getModelYear(),
                detail.getModel(),
                vehicle.getCompany().getId(),
                vehicle.getCompany().getName(),
                vehicle.getCarNumber(),
                vehicle.getMileage()
        );
    }
}
