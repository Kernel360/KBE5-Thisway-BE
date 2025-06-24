package org.thisway.triplog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.domain.PowerLogData;
import org.thisway.log.repository.LogRepository;
import org.thisway.triplog.converter.ReverseGeocodingConverter;
import org.thisway.triplog.dto.CurrentDrivingInfo;
import org.thisway.triplog.dto.CurrentGpsLog;
import org.thisway.triplog.dto.ReverseGeocodeResult;
import org.thisway.triplog.dto.response.CurrentTripLogResponse;
import org.thisway.triplog.dto.response.TripLogDetailResponse;
import org.thisway.triplog.dto.response.TripLogsResponse;
import org.thisway.triplog.dto.response.VehicleDetailResponse;
import org.thisway.triplog.entity.TripLog;
import org.thisway.triplog.repository.TripLogRepository;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.service.VehicleService;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripLogServiceImpl implements TripLogService {

    private final VehicleService vehicleService;

    private final TripLogRepository tripLogRepository;
    private final LogRepository logRepository;

    private final ReverseGeocodingConverter reverseGeocodingConverter;

    @Override
    public VehicleDetailResponse getVehicleDetails(Long vehicleId) {
        VehicleResponse vehicleResponse = vehicleService.getVehicleDetail(vehicleId);
        List<TripLog> tripLogs = tripLogRepository.findTop5ByVehicleIdOrderByStartTimeDesc(vehicleId);
        CurrentDrivingInfo currentDrivingInfo = null;
        // Todo: 현재 위치 log 저장 로직 변경 후 적용 예정
//        if (vehicleResponse.powerOn()) {
//            currentDrivingInfo = getCurrentDrivingInfo(
//                    powerLogs.getLast(),
//                    logRepository.getCurrentGpsByVehicleId(vehicleId)
//            );
//        }

        return VehicleDetailResponse.from(
                vehicleService.getVehicleDetail(vehicleId),
                currentDrivingInfo,
                tripLogs
        );
    }

    @Override
    public CurrentTripLogResponse getCurrentGpsLogs(Long vehicleId, LocalDateTime time) {
        // Todo: 현재 운행중인 차량인지 확인하는 로직 추가
        List<GpsLogData> gpsLogs = logRepository.findGpsLogsByVehicleId(vehicleId, time, LocalDateTime.now());
        List<CurrentGpsLog> currentGpsLogs = gpsLogs.stream()
                .map(CurrentGpsLog::from)
                .toList();

        if (!gpsLogs.isEmpty()) {
            return CurrentTripLogResponse.from(gpsLogs.getLast(), currentGpsLogs);
        } else {
            throw new CustomException(ErrorCode.TRIP_LOG_NOT_FOUND);
        }
    }

    @Override
    public TripLogsResponse getTripLogs(Long companyId, Pageable pageable) {
        Page<TripLog> TripLogs = tripLogRepository.findAllByCompanyOrderByStartTimeDesc(companyId, pageable);

        return TripLogsResponse.from(TripLogs);
    }

    @Override
    public TripLogDetailResponse getTripLogDetails(Long vehicleId, LocalDateTime start, LocalDateTime end) {
        List<PowerLogData> powerLogs = logRepository.findPowerLogsByVehicleIdAndPowerTime(vehicleId, start);
        List<GpsLogData> gpsLogs = logRepository.findGpsLogsByVehicleId(vehicleId, start, end);

        if (powerLogs.size() == 2 && powerLogs.getFirst().powerTime().equals(start) && powerLogs.getLast().powerTime()
                .equals(end)) {
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

    @Override
    public void saveTripLog(PowerLogData powerOnLog, PowerLogData powerOffLog) {
        Vehicle vehicle = vehicleService.findVehicleById(powerOnLog.vehicleId());
        ReverseGeocodeResult onResult = reverseGeocodingConverter.convertToAddress(powerOnLog.latitude(), powerOnLog.longitude());
        ReverseGeocodeResult offResult = reverseGeocodingConverter.convertToAddress(powerOffLog.latitude(), powerOffLog.longitude());

        TripLog tripLog = TripLog.builder()
                .vehicle(vehicle)
                .startTime(powerOnLog.powerTime())
                .endTime(powerOffLog.powerTime())
                .totalTripMeter(powerOffLog.totalTripMeter())
                .onLatitude(powerOnLog.latitude())
                .onLongitude(powerOnLog.longitude())
                .onAddress(onResult.addr())
                .onAddrDetail(onResult.addrDetail())
                .offLatitude(powerOffLog.latitude())
                .offLongitude(powerOffLog.longitude())
                .offAddress(offResult.addr())
                .offAddrDetail(offResult.addrDetail())
                .build();

        tripLogRepository.save(tripLog);
    }

}
