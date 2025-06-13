package org.thisway.vehicle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.vehicle.validation.ValidCarNumber;
import org.thisway.company.entity.Company;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleModel;

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

