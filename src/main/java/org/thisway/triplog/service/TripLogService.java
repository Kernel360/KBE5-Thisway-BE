package org.thisway.triplog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.domain.PowerLogData;
import org.thisway.log.repository.LogRepository;
import org.thisway.triplog.dto.CurrentDrivingInfo;
import org.thisway.triplog.dto.CurrentGpsLog;
import org.thisway.triplog.dto.TripLogBriefInfo;
import org.thisway.triplog.dto.response.CurrentTripLogResponse;
import org.thisway.triplog.dto.response.TripLogDetailResponse;
import org.thisway.triplog.dto.response.VehicleDetailResponse;
import org.thisway.vehicle.service.VehicleService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripLogService {

    private final VehicleService vehicleService;
    private final LogRepository logRepository;

    public VehicleDetailResponse getVehicleDetails(Long vehicleId) {
        List<PowerLogData> powerLogs = logRepository.findPowerLogsByVehicleId(vehicleId);

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
                convertToTripLogsFromPowerLogs(powerLogs)
        );
    }

    public CurrentTripLogResponse getCurrentGpsLogs(Long vehicleId, LocalDateTime time) {
        // Todo: 현재 운행중인 차량인지 확인하는 로직 추가
        List<GpsLogData> gpsLogs = logRepository.findGpsLogsByVehicleId(vehicleId, time, LocalDateTime.now());
        List<CurrentGpsLog> currentGpsLogs = gpsLogs.stream()
                .map(CurrentGpsLog::from)
                .toList();

        return CurrentTripLogResponse.from(gpsLogs.getLast(),currentGpsLogs);
    }

    public List<TripLogBriefInfo> getTripLogs() {
        List<PowerLogData> powerLogs = logRepository.findAllPowerLogs();

        return extractAllTripLogs(powerLogs);
    }

    public TripLogDetailResponse getTripLogDetails(Long vehicleId, LocalDateTime start, LocalDateTime end) {
        List<PowerLogData> powerLogs = logRepository.findPowerLogsByVehicleIdAndPowerTime(vehicleId, start);
        List<GpsLogData> gpsLogs = logRepository.findGpsLogsByVehicleId(vehicleId, start, end);

        if (powerLogs.size() == 2 && powerLogs.getFirst().powerTime().equals(start) && powerLogs.getLast().powerTime().equals(end)) {
            return TripLogDetailResponse.from(
                    vehicleService.findVehicleById(vehicleId),
                    powerLogs.getFirst(),
                    powerLogs.getLast(),
                    gpsLogs.stream().map(CurrentGpsLog::from).toList(),
                    gpsLogs.stream().mapToInt(GpsLogData::speed).average().orElse(0)
            );
        } else {
            throw new CustomException(ErrorCode.TRIP_LOG_NOT_FOUND);
        }
    }

    private CurrentDrivingInfo getCurrentDrivingInfo(PowerLogData powerLogData, GpsLogData gpsLogData) {
        return CurrentDrivingInfo.from(
                powerLogData,
                gpsLogData
        );
    }

    private List<TripLogBriefInfo> extractAllTripLogs(List<PowerLogData> logs) {
        Map<Long, List<PowerLogData>> powerLogsByVehicle = logs.stream()
                .sorted(Comparator.comparing(PowerLogData::vehicleId)
                        .thenComparing(PowerLogData::powerTime))
                .collect(Collectors.groupingBy(
                        PowerLogData::vehicleId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<TripLogBriefInfo> allTripLogs = new ArrayList<>();
        for (List<PowerLogData> vehicleLogs : powerLogsByVehicle.values()) {
            allTripLogs.addAll(convertToTripLogsFromPowerLogs(vehicleLogs));
        }

        allTripLogs.sort(Comparator.comparing(TripLogBriefInfo::startTime));

        return allTripLogs;
    }

    private List<TripLogBriefInfo> convertToTripLogsFromPowerLogs(List<PowerLogData> logs) {
        List<TripLogBriefInfo> tripLogBriefInfos = new ArrayList<>();
        PowerLogData onLog = null;

        for (PowerLogData log : logs) {
            if (log.powerStatus()) {
                onLog = log;
            } else {
                if (onLog != null) {
                    int tripMeter = log.totalTripMeter() - onLog.totalTripMeter();
                    TripLogBriefInfo tripLogBriefInfo = new TripLogBriefInfo(
                            onLog.vehicleId(),
                            vehicleService.findVehicleById(onLog.vehicleId()).getCarNumber(),
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
