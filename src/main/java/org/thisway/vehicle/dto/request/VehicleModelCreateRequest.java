package org.thisway.vehicle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.vehicle.entity.VehicleModel;

public record VehicleModelCreateRequest(
        @NotBlank(message = "14007")
        String manufacturer,
        
        @NotNull(message = "14008")
        Integer modelYear,
        
        @NotBlank(message = "14009")
        String name
) {

    public VehicleModel toEntity() {
        return VehicleModel.builder()
                .manufacturer(this.manufacturer())
                .modelYear(this.modelYear())
                .name(this.name())
                .build();
    }
}
