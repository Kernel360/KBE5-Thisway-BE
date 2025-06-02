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

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LogService {

    private final EmulatorRepository emulatorRepository;
    private final LogRepository logRepository;
    private final LogDataConverter converter;

    public void savePowerLog(PowerLogRequest request) {
        log.info("시동 정보 로그 수신: MDN={}, onTime={}, offTime={}",
                request.mdn(), request.onTime(), request.offTime());

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        if (request.onTime() != null && !request.onTime().isEmpty()){
            PowerLogData powerLogData = PowerLogData.from(
                    request, vehicleId, true, request.onTime(), converter);
            logRepository.savePowerLog(powerLogData);
        }

        if (request.offTime() != null && !request.offTime().isEmpty()){
            PowerLogData powerLogData = PowerLogData.from(
                    request, vehicleId, false, request.offTime(), converter);
            logRepository.savePowerLog(powerLogData);
        }

        log.info("시동 정보 로그 저장 완료: MDN={}", request.mdn());
    }

    public void saveGpsLog(GpsLogRequest request) {
        log.info("주기 정보 로그 수신: MDN={}, 항목 수={}", request.mdn(), request.cCnt());

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        List<GpsLogData> gpsLogDataList = new ArrayList<>();

        for (GpsLogEntry entry : request.cList()) {
            LocalDateTime occurredTime = converter.convertDateTime(request.oTime());

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
