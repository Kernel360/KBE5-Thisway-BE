package org.thisway.vehicle.dto.request;

public record VehicleUpdateRequest(
        String carNumber,
        String color,
        String manufacturer,
        Integer modelYear,
        String model
) {}
