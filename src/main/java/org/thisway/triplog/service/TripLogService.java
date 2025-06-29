package org.thisway.triplog.service;

import org.springframework.data.domain.Pageable;
import org.thisway.triplog.dto.TripLogSaveInput;
import org.thisway.triplog.dto.response.CurrentTripLogResponse;
import org.thisway.triplog.dto.response.TripLogDetailResponse;
import org.thisway.triplog.dto.response.TripLogsResponse;
import org.thisway.triplog.dto.response.VehicleDetailResponse;


import java.time.LocalDateTime;

public interface TripLogService {

    VehicleDetailResponse getVehicleDetails(Long vehicleId);

    CurrentTripLogResponse getCurrentGpsLogs(Long vehicleId, LocalDateTime time);

    TripLogsResponse findTripLogs(Long companyId, Pageable pageable);

    TripLogDetailResponse getTripLogDetails(Long tripLogId);

    void saveTripLog(TripLogSaveInput tripLogSaveInput);

}
