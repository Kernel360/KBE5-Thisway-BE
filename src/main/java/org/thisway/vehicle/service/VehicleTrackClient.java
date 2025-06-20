package org.thisway.vehicle.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.thisway.vehicle.dto.response.VehicleTracksResponse;

@Component
public interface VehicleTrackClient {

    VehicleTracksResponse trackVehicles(long companyId, Pageable pageable);
}
