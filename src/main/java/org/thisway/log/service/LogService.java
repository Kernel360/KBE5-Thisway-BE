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
import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;
import org.thisway.log.dto.request.gpsLog.GpsLogEntry;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;
import org.thisway.log.repository.LogRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final EmulatorRepository emulatorRepository;
    private final LogRepository logRepository;
    private final LogDataConverter converter;

    @Transactional
    public void savePowerLog(PowerLogRequest request) {
        log.info("시동 정보 로그 수신: MDN={}, onTime={}, offTime={}",
                request.mdn(), request.onTime(), request.offTime());

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        if (request.onTime() != null && !request.onTime().isEmpty()){
            LocalDateTime powerTime = converter.convertDateTimeWithSec(request.onTime());

            Double latitude = converter.convertCoordinate(request.lat());
            Double longitude = converter.convertCoordinate(request.lon());
            Integer totalTripMeter = converter.convertToInteger(request.sum());

            logRepository.savePowerLog(
                    vehicleId,
                    mdn,
                    true,
                    powerTime,
                    request.gcd(),
                    latitude,
                    longitude,
                    totalTripMeter
            );
        }

        if (request.offTime() != null && !request.offTime().isEmpty()){
            LocalDateTime powerTime = converter.convertDateTimeWithSec(request.offTime());

            Double latitude = converter.convertCoordinate(request.lat());
            Double longitude = converter.convertCoordinate(request.lon());
            Integer totalTripMeter = converter.convertToInteger(request.sum());

            logRepository.savePowerLog(
                    vehicleId,
                    mdn,
                    false,
                    powerTime,
                    request.gcd(),
                    latitude,
                    longitude,
                    totalTripMeter
            );
        }



        log.info("시동 정보 로그 저장 완료: MDN={}", request.mdn());
    }


    @Transactional
    public void saveGpsLog(GpsLogRequest request) {
        log.info("주기 정보 로그 수신: MDN={}, 항목 수={}", request.mdn(), request.cCnt());

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        List<Object[]> gpsLogBatch = new ArrayList<>();

        for (GpsLogEntry entry : request.cList()) {
            LocalDateTime occurredTime = converter.convertDateTime(request.oTime());

            Double latitude = converter.convertCoordinate(entry.lat());
            Double longitude = converter.convertCoordinate(entry.lon());
            Integer angle = converter.convertToInteger(entry.ang());
            Integer speed = converter.convertToInteger(entry.spd());
            Integer totalTripMeter = converter.convertToInteger(entry.sum());
            Integer batteryVoltage = converter.convertToInteger(entry.bat());

            gpsLogBatch.add(new Object[]{
                    vehicleId,
                    mdn,
                    entry.gcd(),
                    latitude,
                    longitude,
                    angle,
                    speed,
                    totalTripMeter,
                    batteryVoltage,
                    occurredTime
            });
        }

        logRepository.saveGpsLogs(gpsLogBatch);

        log.info("주기 정보 로그 저장 완료: MDN={}, 항목 수={}", request.mdn(), gpsLogBatch.size());
    }

    @Transactional
    public void saveGeofenceLog(GeofenceLogRequest request) {
        log.info("지오펜스 정보 로그 수신: MDN={}, geoGrpId={}, geoPId={}",
                request.mdn(), request.geoGrpId(), request.geoPId());

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        LocalDateTime occurredTime = converter.convertDateTimeWithSec(request.oTime());

        Double latitude = converter.convertCoordinate(request.lat());
        Double longitude = converter.convertCoordinate(request.lon());
        Integer angle = converter.convertToInteger(request.ang());
        Long geofenceGroupId = converter.convertToLong(request.geoGrpId());
        Long geofenceId = converter.convertToLong(request.geoPId());
        Byte eventVal = converter.convertToByte(request.evtVal());

        logRepository.saveGeofenceLog(
                vehicleId,
                mdn,
                occurredTime,
                geofenceGroupId,
                geofenceId,
                eventVal,
                request.gcd(),
                latitude,
                longitude,
                angle
        );

        log.info("지오펜스 정보 로그 저장 완료: MDN={}", request.mdn());
    }

    private Long getVehicleIdByMdn(String mdn) {
        Emulator emulator = emulatorRepository.findByMdn(mdn)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));
        return emulator.getVehicle().getId();
    }
}
