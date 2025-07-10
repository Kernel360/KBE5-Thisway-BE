package org.thisway.vehicle.triplog.application;

import org.springframework.data.domain.Pageable;
import org.thisway.vehicle.triplog.domain.CoordinatesInfo;
import org.thisway.vehicle.triplog.domain.TripLogSaveInput;
import org.thisway.vehicle.triplog.interfaces.CurrentTripLogResponse;
import org.thisway.vehicle.triplog.interfaces.TripLogDetailResponse;
import org.thisway.vehicle.triplog.interfaces.TripLogsResponse;
import org.thisway.vehicle.triplog.interfaces.VehicleDetailResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface TripLogService {

    VehicleDetailResponse getVehicleDetails(Long vehicleId);

    CurrentTripLogResponse getCurrentGpsLogs(Long vehicleId, LocalDateTime time);

    TripLogsResponse findTripLogs(Long companyId, Pageable pageable);

    TripLogDetailResponse getTripLogDetails(Long tripId);

    LocalDateTime getLastStartTimeByVehicle(Long vehicleId);

    void saveTripLog(TripLogSaveInput tripLogSaveInput);

    List<CoordinatesInfo> getGpsLogsInTripLog(Long tripId);
}
