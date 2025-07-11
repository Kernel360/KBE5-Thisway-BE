package org.thisway.vehicle.domain;

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
