package org.thisway.vehicle.log.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.emulator.domain.Emulator;
import org.thisway.emulator.infrastructure.EmulatorRepository;
import org.thisway.vehicle.log.util.LogDataConverter;
import org.thisway.vehicle.log.domain.GeofenceLogData;
import org.thisway.vehicle.log.domain.GpsLogData;
import org.thisway.vehicle.log.domain.PowerLogData;
import org.thisway.vehicle.log.interfaces.GeofenceLogRequest;
import org.thisway.vehicle.log.interfaces.PowerLogRequest;
import org.thisway.vehicle.log.interfaces.PowerLogRequestValidator;
import org.thisway.vehicle.log.infrastructure.LogRepository;
import org.thisway.vehicle.triplog.domain.TripLogSaveInput;
import org.thisway.vehicle.triplog.application.TripLogService;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.application.VehicleService;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final EmulatorRepository emulatorRepository;
    private final LogRepository logRepository;
    private final LogDataConverter converter;

    private final VehicleService vehicleService;
    private final TripLogService tripLogService;

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void savePowerLog(PowerLogRequest request) {
        PowerLogRequestValidator.validate(request);
        log.info("시동 정보 로그 수신: onTime={}, offTime={}", request.onTime(), request.offTime());

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        Vehicle vehicle = vehicleService.getVehicleForPowerUpdate(vehicleId);

        if ((request.onTime() != null && !request.onTime().isEmpty()) &&
                (request.offTime() == null || request.offTime().isEmpty())) {
            PowerLogData powerLogData = PowerLogData.from(
                    request, vehicleId, true, request.onTime(), converter);
            logRepository.savePowerLog(powerLogData);

            vehicle.observePowerEvent(powerLogData.powerTime(), true,
                    powerLogData.latitude(), powerLogData.longitude());
            vehicleService.saveVehicle(vehicle);

            log.info("시동 ON 정보 로그 저장: onTime={}", request.onTime());
        }

        if (request.offTime() != null && !request.offTime().isEmpty()) {
            PowerLogData powerLogData = PowerLogData.from(
                    request, vehicleId, false, request.offTime(), converter);
            logRepository.savePowerLog(powerLogData);

            Integer totalTripMeter = converter.convertToInteger(request.sum());
            if (totalTripMeter == null || totalTripMeter < 0) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            vehicle.observeOdometer(totalTripMeter);
            vehicle.observePowerEvent(powerLogData.powerTime(), false,
                    powerLogData.latitude(), powerLogData.longitude());
            vehicleService.saveVehicle(vehicle);
            log.info("시동 OFF 정보 로그 저장: offTime={}", request.offTime());
        }

        tripLogService.saveTripLog(
                TripLogSaveInput.from(vehicle, request, converter)
        );
        log.info("운행 기록 저장: onTime={}, offTime={}", request.onTime(), request.offTime());

        log.info("시동 정보 로그 저장 완료");
    }

    @Override
    public void saveGeofenceLog(GeofenceLogRequest request) {
        org.thisway.vehicle.log.interfaces.GeofenceLogRequestValidator.validate(request);
        log.info("지오펜스 정보 로그 수신");

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        GeofenceLogData geofenceLogData = GeofenceLogData.from(request, vehicleId, converter);
        logRepository.saveGeofenceLog(geofenceLogData);

        log.info("지오펜스 정보 로그 저장 완료");
    }

    private Long getVehicleIdByMdn(String mdn) {
        var reference = emulatorRepository.findVehicleByMdn(mdn)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));
        // Projection avoids eager-loading a stale Vehicle before acquiring its write lock.
        return reference.id();
    }

    @Override
    public List<GpsLogData> findGpsLogs(Long Id, LocalDateTime start, LocalDateTime end) {
        return logRepository.findGpsLogsByVehicleId(Id, start, end);
    }

    @Override
    public GpsLogData getCurrentGpsLog(Long Id, LocalDateTime start) {
        return logRepository.getCurrentGpsByVehicleId(Id, start);
    }
}
