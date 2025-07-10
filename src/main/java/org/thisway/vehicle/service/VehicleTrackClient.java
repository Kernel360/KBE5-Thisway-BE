package org.thisway.vehicle.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.thisway.vehicle.dto.response.VehicleTrackResponse;
import org.thisway.vehicle.dto.response.VehicleTracksResponse;

import java.util.List;

@Component
public interface VehicleTrackClient {

    VehicleTracksResponse trackVehicles(long companyId, Pageable pageable);

    List<VehicleTrackResponse> trackVehicles(long companyId);
}
