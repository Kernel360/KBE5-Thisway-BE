package org.thisway.log.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.emulator.entity.Emulator;
import org.thisway.emulator.repository.EmulatorRepository;
import org.thisway.log.converter.LogDataConverter;
import org.thisway.log.domain.GeofenceLogData;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.domain.PowerLogData;
import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;
import org.thisway.log.dto.request.gpsLog.GpsLogEntry;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;
import org.thisway.log.repository.LogRepository;
import org.thisway.vehicle.repository.VehicleRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LogService {

    private final VehicleRepository vehicleRepository;
    private final EmulatorRepository emulatorRepository;
    private final LogRepository logRepository;
    private final LogDataConverter converter;

    public void savePowerLog(PowerLogRequest request) {
        log.info("시동 정보 로그 수신: MDN={}, onTime={}, offTime={}",
                request.mdn(), request.onTime(), request.offTime());

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        if ((request.onTime() != null && !request.onTime().isEmpty()) &&
                (request.offTime() == null || request.offTime().isEmpty())) {
            PowerLogData powerLogData = PowerLogData.from(
                    request, vehicleId, true, request.onTime(), converter);
            logRepository.savePowerLog(powerLogData);
            vehicleRepository.findById(vehicleId).ifPresent(vehicle -> {
                vehicle.updatePowerOn(true);
                
                vehicleRepository.save(vehicle);
            });
            log.info("시동 ON 정보 로그 저장: MDN={}, onTime={}", request.mdn(), request.onTime());
        }

        if (request.offTime() != null && !request.offTime().isEmpty()) {
            PowerLogData powerLogData = PowerLogData.from(
                    request, vehicleId, false, request.offTime(), converter);
            logRepository.savePowerLog(powerLogData);
            vehicleRepository.findById(vehicleId).ifPresent(vehicle -> {
                Integer totalTripMeter = converter.convertToInteger(request.sum());
                vehicle.updatePowerOn(false);

                vehicle.updateMileage(totalTripMeter);
                vehicle.updateLocation(
                        converter.convertCoordinate(request.lat()),
                        converter.convertCoordinate(request.lon())
                );

                vehicleRepository.save(vehicle);
            });
            log.info("시동 OFF 정보 로그 저장: MDN={}, offTime={}, totalTripMeter={}",
                    request.mdn(), request.offTime(), request.sum());
        }

        log.info("시동 정보 로그 저장 완료: MDN={}", request.mdn());
    }

    public void saveGpsLog(GpsLogRequest request) {
        log.info("주기 정보 로그 수신: MDN={}, 항목 수={}, 시간={}", request.mdn(), request.cCnt(), request.oTime());

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        List<GpsLogData> gpsLogDataList = new ArrayList<>();

        LocalDateTime occurredTime;
        try {
            if (request.oTime().length() == 14) {
                occurredTime = converter.convertDateTimeWithSec(request.oTime());
                log.info("초 단위 시간 형식 감지: {}", request.oTime());
            } else {
                occurredTime = converter.convertDateTime(request.oTime());
                log.info("분 단위 시간 형식 감지: {}", request.oTime());
            }
        } catch (Exception e) {
            log.error("시간 형식 변환 오류: {}, 오류 메시지: {}", request.oTime(), e.getMessage());
            throw new CustomException(ErrorCode.SERVER_ERROR);
        }

        for (GpsLogEntry entry : request.cList()) {
            GpsLogData gpsLogData = GpsLogData.from(entry, mdn, vehicleId, occurredTime, converter);
            gpsLogDataList.add(gpsLogData);
        }

        logRepository.saveGpsLogs(gpsLogDataList);

        log.info("주기 정보 로그 저장 완료: MDN={}, 항목 수={}", request.mdn(), gpsLogDataList.size());
    }

    public void saveGeofenceLog(GeofenceLogRequest request) {
        log.info("지오펜스 정보 로그 수신: MDN={}, geoGrpId={}, geoPId={}",
                request.mdn(), request.geoGrpId(), request.geoPId());

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        GeofenceLogData geofenceLogData = GeofenceLogData.from(request, vehicleId, converter);
        logRepository.saveGeofenceLog(geofenceLogData);

        log.info("지오펜스 정보 로그 저장 완료: MDN={}", request.mdn());
    }

    private Long getVehicleIdByMdn(String mdn) {
        Emulator emulator = emulatorRepository.findByMdn(mdn)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));
        return emulator.getVehicle().getId();
    }
}
