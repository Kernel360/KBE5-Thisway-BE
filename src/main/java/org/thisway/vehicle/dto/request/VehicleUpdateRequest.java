package org.thisway.vehicle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleUpdateRequest(
        @NotBlank(message = "차량 번호는 필수입니다")
        String carNumber,

        @NotBlank(message = "차량 색상은 필수입니다")
        String color,

        @NotBlank(message = "제조사는 필수입니다")
        String manufacturer,

        @NotNull(message = "연식은 필수입니다")
        Integer modelYear,

        @NotBlank(message = "모델명은 필수입니다")
        String model
) {}
