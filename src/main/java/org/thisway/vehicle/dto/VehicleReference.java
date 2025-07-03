package org.thisway.vehicle.dto;

import org.thisway.vehicle.entity.Vehicle;

public record VehicleReference(
        Long id,
        Long companyId
) {
    public static VehicleReference from(Vehicle vehicle) {
        return new VehicleReference(
                vehicle.getId(),
                vehicle.getCompany().getId()
        );
    }
}
