package org.thisway.vehicle.triplog.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.vehicle.application.VehicleService;
import org.thisway.vehicle.interfaces.VehicleResponse;
import org.thisway.vehicle.log.application.LogService;
import org.thisway.vehicle.log.domain.GpsLogData;
import org.thisway.vehicle.triplog.domain.*;
import org.thisway.vehicle.triplog.infrastructure.TripLogRepository;
import org.thisway.vehicle.triplog.interfaces.CurrentTripLogResponse;
import org.thisway.vehicle.triplog.interfaces.TripLogDetailResponse;
import org.thisway.vehicle.triplog.interfaces.TripLogsResponse;
import org.thisway.vehicle.triplog.interfaces.VehicleDetailResponse;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TripLogServiceImpl implements TripLogService {

    private final VehicleService vehicleService;
    private final LogService logService;
    private final TripLogRepository tripLogRepository;
    private final ReverseGeocodingConverter reverseGeocodingConverter;

    public TripLogServiceImpl(
            VehicleService vehicleService,
            @Lazy LogService logService,
            TripLogRepository tripLogRepository,
            ReverseGeocodingConverter reverseGeocodingConverter
    ) {
        this.vehicleService = vehicleService;
        this.logService = logService;
        this.tripLogRepository = tripLogRepository;
        this.reverseGeocodingConverter = reverseGeocodingConverter;
    }

    @Override
    public VehicleDetailResponse getVehicleDetails(Long vehicleId) {
        VehicleResponse vehicleResponse = vehicleService.getVehicleDetail(vehicleId);
        List<TripLog> tripLogs = tripLogRepository.findTop6ByVehicleIdOrderByStartTimeDesc(vehicleId);
        CurrentDrivingInfo currentDrivingInfo = null;

        if (!tripLogs.isEmpty() && vehicleResponse.powerOn()) {
            currentDrivingInfo = CurrentDrivingInfo.from(
                    tripLogs.getFirst(),
                    logService.getCurrentGpsLog(vehicleId, tripLogs.getFirst().getStartTime())
            );

            tripLogs.removeFirst();
        }

        return VehicleDetailResponse.from(
                vehicleService.getVehicleDetail(vehicleId),
                currentDrivingInfo,
                tripLogs
        );
    }

    @Override
    public CurrentTripLogResponse getCurrentGpsLogs(Long vehicleId, LocalDateTime time) {
        if (vehicleService.getVehiclePowerState(vehicleId)) {
            List<GpsLogData> gpsLogs = logService.findGpsLogs(vehicleId, time, LocalDateTime.now(ZoneId.of("Asia/Seoul")));

            if (!gpsLogs.isEmpty()) {
                return CurrentTripLogResponse.from(gpsLogs);
            } else {
                return null;
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
    public TripLogDetailResponse getTripLogDetails(Long tripId) {
        TripLog tripLog = tripLogRepository.findById(tripId).orElseThrow(() -> new CustomException(ErrorCode.TRIP_LOG_NOT_FOUND));
        List<GpsLogData> gpsLogs = logService.findGpsLogs(tripLog.getVehicle().getId(), tripLog.getStartTime(), tripLog.getEndTime());

        return TripLogDetailResponse.from(
                tripLog.getVehicle(),
                tripLog,
                gpsLogs.stream().mapToInt(GpsLogData::speed).average().orElse(0)
        );
    }

    @Override
    public LocalDateTime getLastStartTimeByVehicle(Long vehicleId) {
        return tripLogRepository.findTop1StartTimeByVehicleId(vehicleId);
    }

    @Override
    public List<CoordinatesInfo> getGpsLogsInTripLog(Long tripId) {
        TripLog tripLog = tripLogRepository.findById(tripId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_LOG_NOT_FOUND));
        return logService.findGpsLogs(
                tripLog.getVehicle().getId(),
                tripLog.getStartTime(),
                tripLog.getEndTime()
        ).stream().map(CoordinatesInfo::from).toList();
    }

    @Override
    @Transactional
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
