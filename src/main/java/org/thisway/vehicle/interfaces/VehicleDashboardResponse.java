package org.thisway.vehicle.interfaces;

public record VehicleDashboardResponse(
        long totalVehicles,
        long powerOnVehicles,
        long powerOffVehicles
) {
}
