package org.thisway.vehicle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.vehicle.validation.ValidCarNumber;
import org.thisway.company.entity.Company;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleDetail;

public record VehicleCreateRequest(

        @NotBlank
        String manufacturer,

        @NotNull
        Integer modelYear,

        @NotBlank
        String model,

        @NotBlank
        @ValidCarNumber
        String carNumber,

        @NotBlank
        String color
) {
        public VehicleDetail toVehicleDetailEntity() {
                return VehicleDetail.builder()
                        .manufacturer(this.manufacturer)
                        .modelYear(this.modelYear)
                        .model(this.model)
                        .build();
        }

        public Vehicle toVehicleEntity(Company company, VehicleDetail vehicleDetail) {
                return Vehicle.builder()
                        .vehicleDetail(vehicleDetail)
                        .company(company)
                        .carNumber(this.carNumber)
                        .color(this.color)
                        .build();
        }
}
