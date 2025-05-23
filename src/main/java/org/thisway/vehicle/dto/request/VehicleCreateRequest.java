package org.thisway.vehicle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.common.validation.ValidCarNumber;
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

        @ValidCarNumber
        @NotBlank
        String carNumber,

        @NotBlank
        String color,

        @NotNull
        Integer mileage,

        boolean isOn,

        @NotNull
        Double latitude,

        @NotNull
        Double longitude
) {
        public static VehicleDetail vehicleDetailToEntity(VehicleCreateRequest request){
                return VehicleDetail.builder()
                    .manufacturer(request.manufacturer)
                    .modelYear(request.modelYear)
                    .model(request.model)
                    .build();
        }

        public static Vehicle vehicleToEntity(VehicleCreateRequest request, Company company, VehicleDetail vehicleDetail){

                return Vehicle.builder()
                    .vehicleDetail(vehicleDetail)
                    .company(company)
                    .carNumber(request.carNumber)
                    .color(request.color)
                    .mileage(request.mileage)
                    .isOn(request.isOn)
                    .latitude(request.latitude)
                    .longitude(request.longitude)
                    .build();
        }
}
