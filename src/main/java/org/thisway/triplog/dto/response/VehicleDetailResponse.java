package org.thisway.triplog.dto.response;

import org.thisway.triplog.dto.CurrentDrivingInfo;
import org.thisway.triplog.dto.TripLogBriefInfo;
import org.thisway.triplog.entity.TripLog;
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
            List<TripLog> tripLogs
    ) {
        return new VehicleDetailResponse(
                vehicleResponse,
                currentDrivingInfo,
                tripLogs.stream()
                        .map(TripLogBriefInfo::from)
                        .toList()
        );
    }

}
