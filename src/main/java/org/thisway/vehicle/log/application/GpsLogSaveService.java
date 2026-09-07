package org.thisway.vehicle.log.application;

import org.springframework.transaction.annotation.Transactional;
import org.thisway.emulator.credential.DeviceIdentity;
import org.thisway.emulator.credential.DeviceBindingGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.emulator.domain.Emulator;
import org.thisway.emulator.infrastructure.EmulatorRepository;
import org.thisway.vehicle.log.util.LogDataConverter;
import org.thisway.vehicle.log.domain.GpsLogData;
import org.thisway.vehicle.log.interfaces.GpsLogEntry;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.vehicle.log.interfaces.GpsLogRequestValidator;
import org.thisway.vehicle.log.infrastructure.LogRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GpsLogSaveService {

    private final EmulatorRepository emulatorRepository;
    private final LogRepository logRepository;
    private final LogDataConverter converter;
    private final DeviceBindingGuard bindingGuard;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional
    public void saveGpsLog(GpsLogRequest request, DeviceIdentity identity) {
        GpsLogRequestValidator.validate(request);
        bindingGuard.requireCurrent(identity, request.mdn());
        var observations = persist(request, identity.vehicleId());
        var first = observations.stream().map(GpsLogData::occurredTime).min(LocalDateTime::compareTo).orElseThrow();
        var last = observations.stream().map(GpsLogData::occurredTime).max(LocalDateTime::compareTo).orElseThrow();
        events.publishEvent(new org.thisway.company.statistics.application.StatisticsSourceChanged(
                identity.companyId(), first.toLocalDate(), last.toLocalDate(), "GPS_OBSERVED"));
    }

    // Internal legacy characterization entry point; never used by HTTP or broker consumers.
    public void saveGpsLog(GpsLogRequest request) {
        GpsLogRequestValidator.validate(request);
        persist(request, getVehicleIdByMdn(request.mdn()));
    }

    private List<GpsLogData> persist(GpsLogRequest request, Long vehicleId) {
        GpsLogRequestValidator.validate(request);
        log.debug("event=gps_storage_started");

        String mdn = request.mdn();

        List<GpsLogData> gpsLogDataList = new ArrayList<>();

        LocalDateTime baseTime;
        try {
            if (request.oTime().length() == 14) {
                baseTime = converter.convertDateTimeWithSec(request.oTime());
            } else {
                baseTime = converter.convertDateTime(request.oTime());
            }
        } catch (Exception e) {
            log.warn("event=gps_time_conversion_failed");
            throw new CustomException(ErrorCode.SERVER_ERROR);
        }

        for (GpsLogEntry entry : request.cList()) {
            LocalDateTime timeWithMinutes = baseTime;

            if (entry.min() != null && !entry.min().isEmpty()) {
                int minutes = converter.convertToInteger(entry.min());
                timeWithMinutes = timeWithMinutes.withMinute(minutes);
            }

            LocalDateTime occurredTime;
            if (entry.sec() != null && !entry.sec().isEmpty()) {
                int seconds = converter.convertToInteger(entry.sec());
                occurredTime = timeWithMinutes.withSecond(seconds);
            } else {
                occurredTime = timeWithMinutes;
            }

            GpsLogData gpsLogData = GpsLogData.from(entry, mdn, vehicleId, occurredTime, converter);
            gpsLogDataList.add(gpsLogData);
        }

        logRepository.saveGpsLogs(gpsLogDataList);

        log.debug("event=gps_storage_statement_completed");
        return gpsLogDataList;
    }

    private Long getVehicleIdByMdn(String mdn) {
        Emulator emulator = emulatorRepository.findByMdn(mdn)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));
        return emulator.getVehicle().getId();
    }
}
