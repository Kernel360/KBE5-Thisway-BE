package org.thisway.vehicle.dto.response;

public record VehicleDashboardResponse(
        long totalVehicles,
        long powerOnVehicles,
        long powerOffVehicles
) {
}
