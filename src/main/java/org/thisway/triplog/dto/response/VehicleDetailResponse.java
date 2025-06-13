package org.thisway.triplog.dto.response;

import org.thisway.triplog.dto.CurrentDrivingInfo;
import org.thisway.triplog.dto.TripLogBriefInfo;
import org.thisway.vehicle.dto.response.VehicleResponse;

import java.util.List;

public record VehicleDetailResponse(
        VehicleResponse vehicleResponse,
        CurrentDrivingInfo currentDrivingInfo,
        List<TripLogBriefInfo> tripLogBriefInfos
) {
    public static VehicleDetailResponse from(
            VehicleResponse vehicleResponse,
            CurrentDrivingInfo currentDrivingInfo,
            List<TripLogBriefInfo> tripLogBriefInfos
    ) {
        return new VehicleDetailResponse(vehicleResponse, currentDrivingInfo, tripLogBriefInfos);
    }

}
