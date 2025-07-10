package org.thisway.vehicle.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.thisway.vehicle.interfaces.VehicleTrackResponse;
import org.thisway.vehicle.interfaces.VehicleTracksResponse;

import java.util.List;

@Component
public interface VehicleTrackClient {

    VehicleTracksResponse trackVehicles(long companyId, Pageable pageable);

    List<VehicleTrackResponse> trackVehicles(long companyId);
}
