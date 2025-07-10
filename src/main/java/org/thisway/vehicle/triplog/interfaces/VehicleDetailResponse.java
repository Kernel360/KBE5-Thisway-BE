package org.thisway.vehicle.triplog.interfaces;

import org.thisway.vehicle.interfaces.VehicleResponse;
import org.thisway.vehicle.triplog.domain.CurrentDrivingInfo;
import org.thisway.vehicle.triplog.domain.TripLog;
import org.thisway.vehicle.triplog.domain.TripLogBriefInfo;

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
