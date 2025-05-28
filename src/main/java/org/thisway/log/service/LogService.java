package org.thisway.log.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.emulator.entity.Emulator;
import org.thisway.emulator.repository.EmulatorRepository;
import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;
import org.thisway.log.dto.request.gpsLog.GpsLogEntry;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final EmulatorRepository emulatorRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Transactional
    public void saveGpsLog(GpsLogRequest request) {
        log.info("주기 정보 로그 수신: MDN={}, 항목 수={}", request.mdn(), request.cCnt());

        Long mdn = Long.parseLong(request.mdn());
        Long vehicleId = getVehicleIdByMdn(mdn);

        List<Object[]> gpsLogBatch = new ArrayList<>();

        for (GpsLogEntry entry : request.cList()) {
            LocalDateTime occurredTime = parseDateTime(request.oTime(), entry.sec());

            Double latitude = parseCoordinate(entry.lat());
            Double longitude = parseCoordinate(entry.lon());
            Integer angle = Integer.parseInt(entry.ang());
            Integer speed = Integer.parseInt(entry.spd());
            Integer totalTripMeter = Integer.parseInt(entry.sum());
            Integer batteryVoltage = Integer.parseInt(entry.bat());

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

        String gpsLogSql = "INSERT INTO gps_log (vehicle_id, mdn, gps_status, latitude, longitude, anger, speed, total_trip_meter, battery_voltage, occurred_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(gpsLogSql, gpsLogBatch);

        log.info("주기 정보 로그 저장 완료: MDN={}, 항목 수={}", request.mdn(), gpsLogBatch.size());
    }

    @Transactional
    public void savePowerLog(PowerLogRequest request) {
        log.info("시동 정보 로그 수신: MDN={}, onTime={}, offTime={}",
                request.mdn(), request.onTime(), request.offTime());

        Long mdn = Long.parseLong(request.mdn());
        Long vehicleId = getVehicleIdByMdn(mdn);

        LocalDateTime occurredTime = LocalDateTime.parse(request.onTime(), DATE_TIME_FORMATTER);

        Double latitude = parseCoordinate(request.lat());
        Double longitude = parseCoordinate(request.lon());
        Integer totalTripMeter = Integer.parseInt(request.sum());

        Object[] powerLogParams = new Object[]{
                vehicleId,
                mdn,
                "ON",
                occurredTime,
                request.gcd(),
                latitude,
                longitude,
                totalTripMeter
        };

        String powerLogSql = "INSERT INTO power_log ("
                + "vehicle_id, "
                + "mdn, "
                + "power_status, "
                + "power_time,"
                + "gps_status, "
                + "latitude, "
                + "longitude, "
                + "total_trip_meter"
                + ") "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(powerLogSql, powerLogParams);

        log.info("시동 정보 로그 저장 완료: MDN={}", request.mdn());
    }

    @Transactional
    public void saveGeofenceLog(GeofenceLogRequest request) {
        log.info("지오펜스 정보 로그 수신: MDN={}, geoGrpId={}, geoPId={}",
                request.mdn(), request.geoGrpId(), request.geoPId());

        Long mdn = Long.parseLong(request.mdn());
        Long vehicleId = getVehicleIdByMdn(mdn);

        LocalDateTime occurredTime = LocalDateTime.parse(request.oTime(), DATE_TIME_FORMATTER);

        Double latitude = parseCoordinate(request.lat());
        Double longitude = parseCoordinate(request.lon());
        Integer angle = Integer.parseInt(request.ang());
        Long geofenceGroupId = Long.parseLong(request.geoGrpId());
        Long geofenceId = Long.parseLong(request.geoPId());
        Byte eventVal = Byte.parseByte(request.evtVal());

        Object[] geofenceLogParams = new Object[]{
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
        };

        String geofenceLogSql = "INSERT INTO geofence_log (vehicle_id, mdn, occured_time, geofence_group_id, geofence_id, event_val, gps_status, latitude, longitude, angle) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(geofenceLogSql, geofenceLogParams);

        log.info("지오펜스 정보 로그 저장 완료: MDN={}", request.mdn());
    }

    private Long getVehicleIdByMdn(Long mdn) {
        Emulator emulator = emulatorRepository.findByMdn(mdn)
                .orElseThrow(() -> new CustomException(ErrorCode.EMULATOR_NOT_FOUND));
        return emulator.getVehicle().getId();
    }

    private Double parseCoordinate(String coordinate) {
        double value = Double.parseDouble(coordinate);
        return value / 100000;
    }

    private LocalDateTime parseDateTime(String dateTime, String seconds) {
        String fullDateTime = dateTime + seconds;
        return LocalDateTime.parse(fullDateTime, DATE_TIME_FORMATTER);
    }
}
