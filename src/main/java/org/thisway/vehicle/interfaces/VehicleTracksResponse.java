package org.thisway.vehicle.interfaces;

import java.util.List;

import org.springframework.data.domain.Page;
import org.thisway.support.common.PageInfo;

public record VehicleTracksResponse(
        List<VehicleTrackResponse> vehicles,
        PageInfo pageInfo
) {

    public static VehicleTracksResponse from(Page<VehicleTrackResponse> vehiclePage) {
        return new VehicleTracksResponse(
                vehiclePage.getContent(),
                PageInfo.from(vehiclePage)
        );
    }
}
