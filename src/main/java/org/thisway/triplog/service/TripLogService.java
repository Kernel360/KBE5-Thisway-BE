package org.thisway.triplog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.domain.PowerLogData;
import org.thisway.log.repository.LogRepository;
import org.thisway.triplog.dto.CurrentDrivingInfo;
import org.thisway.triplog.dto.TripLogBriefInfo;
import org.thisway.triplog.dto.response.VehicleDetailResponse;
import org.thisway.vehicle.service.VehicleService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripLogService {

    private final VehicleService vehicleService;
    private final LogRepository logRepository;

    public VehicleDetailResponse getVehicleDetails(Long vehicleId) {
        List<PowerLogData> powerLogs = logRepository.findPowerLogByVehicleId(vehicleId);

        CurrentDrivingInfo currentDrivingInfo = null;
        if (!powerLogs.isEmpty() && powerLogs.getLast().powerStatus()) {
            currentDrivingInfo = getCurrentDrivingInfo(
                    powerLogs.getLast(),
                    logRepository.findCurrentGpsByVehicleId(vehicleId)
            );
        }

        return new VehicleDetailResponse(
                vehicleService.getVehicleDetail(vehicleId),
                currentDrivingInfo,
                convertToTripLogs(powerLogs)
        );
    }

    private CurrentDrivingInfo getCurrentDrivingInfo(PowerLogData powerLogData, GpsLogData gpsLogData) {
        return CurrentDrivingInfo.from(
                powerLogData,
                gpsLogData
        );
    }

    private List<TripLogBriefInfo> convertToTripLogs(List<PowerLogData> logs) {
        List<TripLogBriefInfo> tripLogBriefInfos = new ArrayList<>();
        PowerLogData onLog = null;

        for (PowerLogData log : logs) {
            if (log.powerStatus()) {
                onLog = log;
            } else {
                if (onLog != null) {
                    int tripMeter = log.totalTripMeter() - onLog.totalTripMeter();
                    TripLogBriefInfo tripLogBriefInfo = new TripLogBriefInfo(
                            onLog.powerTime(),
                            log.powerTime(),
                            tripMeter,
                            onLog.latitude(),
                            onLog.longitude()
                    );
                    tripLogBriefInfos.add(tripLogBriefInfo);
                    onLog = null;
                }
            }
        }

        return tripLogBriefInfos;
    }
}
