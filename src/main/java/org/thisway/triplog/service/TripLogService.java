package org.thisway.triplog.service;

import org.springframework.data.domain.Pageable;
import org.thisway.triplog.dto.CoordinatesInfo;
import org.thisway.triplog.dto.TripLogSaveInput;
import org.thisway.triplog.dto.response.CurrentTripLogResponse;
import org.thisway.triplog.dto.response.TripLogDetailResponse;
import org.thisway.triplog.dto.response.TripLogsResponse;
import org.thisway.triplog.dto.response.VehicleDetailResponse;

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
