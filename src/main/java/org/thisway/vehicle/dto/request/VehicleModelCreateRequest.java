package org.thisway.vehicle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.vehicle.entity.VehicleModel;

public record VehicleModelCreateRequest(
        @NotBlank
        String manufacturer,
        
        @NotNull
        Integer modelYear,
        
        @NotBlank
        String model
) {

    public VehicleModel toEntity() {
        return VehicleModel.builder()
                .manufacturer(this.manufacturer())
                .modelYear(this.modelYear())
                .model(this.model())
                .build();
    }
}
