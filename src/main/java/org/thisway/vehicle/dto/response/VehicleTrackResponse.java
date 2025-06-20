package org.thisway.vehicle.dto.response;

import lombok.Builder;

@Builder
public record VehicleTrackResponse(
        long vehicleId,
        String carNumber,
        boolean powerOn,
        Double lat,
        Double lng,
        Integer angle
) {
}
