package org.thisway.vehicle.triplog.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.security.service.SecurityService;
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
    private final ApplicationEventPublisher events;
    private final SecurityService securityService;

    public TripLogServiceImpl(
            VehicleService vehicleService,
            @Lazy LogService logService,
            TripLogRepository tripLogRepository,
            ApplicationEventPublisher events,
            SecurityService securityService
    ) {
        this.vehicleService = vehicleService;
        this.logService = logService;
        this.tripLogRepository = tripLogRepository;
        this.events = events;
        this.securityService = securityService;
    }

    @Override
    public VehicleDetailResponse getVehicleDetails(Long vehicleId) {
        long companyId = securityService.getCurrentMemberDetails().getCompanyId();
        VehicleResponse vehicleResponse = vehicleService.getVehicleDetail(vehicleId);
        List<TripLog> tripLogs = tripLogRepository
                .findTop6ByVehicleIdAndVehicleCompanyIdOrderByStartTimeDesc(vehicleId, companyId);
        CurrentDrivingInfo currentDrivingInfo = null;

        if (!tripLogs.isEmpty() && vehicleResponse.powerOn()) {
            currentDrivingInfo = CurrentDrivingInfo.from(
                    tripLogs.getFirst(),
                    logService.getCurrentGpsLog(vehicleId, tripLogs.getFirst().getStartTime())
            );

            tripLogs.removeFirst();
        }

        return VehicleDetailResponse.from(
                vehicleResponse,
                currentDrivingInfo,
                tripLogs
        );
    }

    @Override
    public CurrentTripLogResponse getCurrentGpsLogs(Long vehicleId, LocalDateTime time) {
        VehicleResponse vehicleResponse = vehicleService.getVehicleDetail(vehicleId);
        if (vehicleResponse.powerOn()) {
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
        long companyId = securityService.getCurrentMemberDetails().getCompanyId();
        TripLog tripLog = tripLogRepository.findByIdAndVehicleCompanyIdAndActiveTrue(tripId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_LOG_NOT_FOUND));
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
        long companyId = securityService.getCurrentMemberDetails().getCompanyId();
        TripLog tripLog = tripLogRepository.findByIdAndVehicleCompanyIdAndActiveTrue(tripId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRIP_LOG_NOT_FOUND));
        return logService.findGpsLogs(
                tripLog.getVehicle().getId(),
                tripLog.getStartTime(),
                tripLog.getEndTime()
        ).stream().map(CoordinatesInfo::from).toList();
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void saveTripLog(TripLogSaveInput tripLogSaveInput) {
        try {
            tripLogSaveInput.validate();
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var vehicle = vehicleService.getVehicleForPowerUpdate(tripLogSaveInput.vehicle().getId());
        var existing = tripLogRepository.findTop2ByVehicleIdAndStartTimeOrderByIdAsc(
                vehicle.getId(), tripLogSaveInput.onTime());
        if (existing.size() > 1 || (!existing.isEmpty() && existing.getFirst().getIdentityStartTime() == null)) {
            throw new CustomException(ErrorCode.TRIP_LEGACY_REVIEW_REQUIRED);
        }
        TripLog tripLog = existing.isEmpty() ? TripLog.observed(vehicle, tripLogSaveInput.onTime()) : existing.getFirst();
        try {
            if (!tripLog.observe(tripLogSaveInput)) return;
        } catch (TripObservationConflictException exception) {
            throw new CustomException(ErrorCode.TRIP_EVENT_CONFLICT);
        }

        tripLogRepository.save(tripLog);
        events.publishEvent(new TripAddressRequested(tripLog.getId(), tripLogSaveInput.offTime() != null));
    }

}
