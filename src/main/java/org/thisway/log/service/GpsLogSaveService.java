package org.thisway.log.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.emulator.domain.Emulator;
import org.thisway.emulator.interfaces.EmulatorRepository;
import org.thisway.log.converter.LogDataConverter;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.dto.request.gpsLog.GpsLogEntry;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.repository.LogRepository;

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

    public void saveGpsLog(GpsLogRequest request) {
        log.info("주기 정보 로그 수신: MDN={}, 항목 수={}, 시간={}", request.mdn(), request.cCnt(), request.oTime());

        String mdn = request.mdn();
        Long vehicleId = getVehicleIdByMdn(mdn);

        List<GpsLogData> gpsLogDataList = new ArrayList<>();

        LocalDateTime baseTime;
        try {
            if (request.oTime().length() == 14) {
                baseTime = converter.convertDateTimeWithSec(request.oTime());
                log.info("초 단위 시간 형식 감지: {}", request.oTime());
            } else {
                baseTime = converter.convertDateTime(request.oTime());
                log.info("분 단위 시간 형식 감지: {}", request.oTime());
            }
        } catch (Exception e) {
            log.error("시간 형식 변환 오류: {}, 오류 메시지: {}", request.oTime(), e.getMessage());
            throw new CustomException(ErrorCode.SERVER_ERROR);
        }

        for (GpsLogEntry entry : request.cList()) {
            LocalDateTime timeWithMinutes = baseTime;

            if (entry.min() != null & !entry.min().isEmpty()) {
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

        log.info("주기 정보 로그 저장 완료: MDN={}, 항목 수={}", request.mdn(), gpsLogDataList.size());
    }

    private Long getVehicleIdByMdn(String mdn) {
        Emulator emulator = emulatorRepository.findByMdn(mdn)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND, "mdn: %s".formatted(mdn)));
        return emulator.getVehicle().getId();
    }
}
