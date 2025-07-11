package org.thisway.vehicle.interfaces;

import org.thisway.vehicle.util.ValidCarNumber;

public record VehicleUpdateRequest(
        Long vehicleModelId,

        @ValidCarNumber
        String carNumber,

        String color
) {
}
