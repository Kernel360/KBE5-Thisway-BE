package org.thisway.triplog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.repository.LogRepository;
import org.thisway.triplog.converter.ReverseGeocodingConverter;
import org.thisway.triplog.dto.CurrentDrivingInfo;
import org.thisway.triplog.dto.CurrentGpsLog;
import org.thisway.triplog.dto.ReverseGeocodeResult;
import org.thisway.triplog.dto.TripLogSaveInput;
import org.thisway.triplog.dto.response.CurrentTripLogResponse;
import org.thisway.triplog.dto.response.TripLogDetailResponse;
import org.thisway.triplog.dto.response.TripLogsResponse;
import org.thisway.triplog.dto.response.VehicleDetailResponse;
import org.thisway.triplog.entity.TripLog;
import org.thisway.triplog.repository.TripLogRepository;
import org.thisway.vehicle.dto.response.VehicleResponse;
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
        List<TripLog> tripLogs = tripLogRepository.findTop6ByVehicleIdOrderByStartTimeDesc(vehicleId);
        CurrentDrivingInfo currentDrivingInfo = null;

        if (!tripLogs.isEmpty() && vehicleResponse.powerOn()) {
            currentDrivingInfo = CurrentDrivingInfo.from(
                    tripLogs.getFirst(),
                    logRepository.getCurrentGpsByVehicleId(vehicleId)
            );

            tripLogs = tripLogs.subList(1, 6);
        }

        return VehicleDetailResponse.from(
                vehicleService.getVehicleDetail(vehicleId),
                currentDrivingInfo,
                tripLogs
        );
    }

    @Override
    public CurrentTripLogResponse getCurrentGpsLogs(Long vehicleId, LocalDateTime time) {
        if (vehicleService.getVehicleById(vehicleId).isPowerOn()) {
            List<GpsLogData> gpsLogs = logRepository.findGpsLogsByVehicleId(vehicleId, time, LocalDateTime.now());
            List<CurrentGpsLog> currentGpsLogs = gpsLogs.stream()
                    .map(CurrentGpsLog::from)
                    .toList();

            if (!gpsLogs.isEmpty()) {
                return CurrentTripLogResponse.from(gpsLogs.getLast(), currentGpsLogs);
            } else {
                throw new CustomException(ErrorCode.TRIP_LOG_NOT_FOUND);
            }
        } else {
            throw new CustomException(ErrorCode.VEHICLE_POWER_OFF);
        }
    }

    @Override
    public TripLogsResponse findTripLogs(Long companyId, Pageable pageable) {
        Page<TripLog> TripLogs = tripLogRepository.findAllByCompanyAndActiveTrueOrderByStartTimeDesc(companyId, pageable);

        return TripLogsResponse.from(TripLogs);
    }

    @Override
    public TripLogDetailResponse getTripLogDetails(Long vehicleId, LocalDateTime start, LocalDateTime end) {
        TripLog tripLog = tripLogRepository.findByVehicleIdAndStartTime(vehicleId, start);
        List<GpsLogData> gpsLogs = logRepository.findGpsLogsByVehicleId(vehicleId, start, end);

        if (tripLog != null && tripLog.getStartTime().equals(start) && tripLog.getEndTime().equals(end)) {
            return TripLogDetailResponse.from(
                    vehicleService.getVehicleById(vehicleId),
                    tripLog,
                    gpsLogs.stream().map(CurrentGpsLog::from).toList(),
                    gpsLogs.stream().mapToInt(GpsLogData::speed).average().orElse(0)
            );
        } else {
            throw new CustomException(ErrorCode.TRIP_LOG_NOT_FOUND);
        }
    }

    @Override
    public void saveTripLog(TripLogSaveInput tripLogSaveInput) {
        TripLog tripLog;
        ReverseGeocodeResult address = reverseGeocodingConverter.convertToAddress(tripLogSaveInput.latitude(), tripLogSaveInput.longitude());

        if (tripLogSaveInput.offTime() == null) {
            tripLog = TripLog.builder()
                    .vehicle(tripLogSaveInput.vehicle())
                    .startTime(tripLogSaveInput.onTime())
                    .totalTripMeter(tripLogSaveInput.totalTripMeter())
                    .onLatitude(tripLogSaveInput.latitude())
                    .onLongitude(tripLogSaveInput.longitude())
                    .onAddress(address.addr())
                    .onAddrDetail(address.addrDetail())
                    .active(false)
                    .build();
        } else {
            tripLog = tripLogRepository.findByVehicleIdAndStartTime(tripLogSaveInput.vehicle().getId(), tripLogSaveInput.onTime());

            if (tripLog == null) {
                tripLog = TripLog.builder()
                        .vehicle(tripLogSaveInput.vehicle())
                        .startTime(tripLogSaveInput.onTime())
                        .endTime(tripLogSaveInput.offTime())
                        .totalTripMeter(0)
                        .offLatitude(tripLogSaveInput.latitude())
                        .offLongitude(tripLogSaveInput.longitude())
                        .offAddress(address.addr())
                        .offAddrDetail(address.addrDetail())
                        .active(true)
                        .build();
            } else {
                tripLog.finishTrip(
                        tripLogSaveInput.offTime(),
                        tripLogSaveInput.totalTripMeter(),
                        tripLogSaveInput.latitude(),
                        tripLogSaveInput.longitude(),
                        address.addr(),
                        address.addrDetail()
                );
            }
        }

        tripLogRepository.save(tripLog);
    }

}
