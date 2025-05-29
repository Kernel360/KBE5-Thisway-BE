package org.thisway.vehicle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleUpdateRequest(
        @NotBlank
        String carNumber,

        @NotBlank
        String color,

        @NotBlank
        String manufacturer,

        @NotNull
        Integer modelYear,

        @NotBlank
        String model
) {}
