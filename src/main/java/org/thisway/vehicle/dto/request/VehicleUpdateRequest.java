package org.thisway.vehicle.dto.request;

import org.thisway.vehicle.validation.ValidCarNumber;

public record VehicleUpdateRequest(
        Long vehicleModelId,
        
        @ValidCarNumber
        String carNumber,
        
        String color
) {}
