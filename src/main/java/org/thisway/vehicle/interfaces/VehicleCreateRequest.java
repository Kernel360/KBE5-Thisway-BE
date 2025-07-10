package org.thisway.vehicle.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.company.domain.Company;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.util.ValidCarNumber;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;

public record VehicleCreateRequest(

        @NotNull
        Long vehicleModelId,

        @NotBlank
        @ValidCarNumber
        String carNumber,

        @NotBlank
        String color
) {
    public Vehicle toVehicleEntity(Company company, VehicleModel vehicleModel) {
        return Vehicle.builder()
                .vehicleModel(vehicleModel)
                .company(company)
                .mileage(0)
                .carNumber(this.carNumber)
                .color(this.color)
                .build();
    }
}

