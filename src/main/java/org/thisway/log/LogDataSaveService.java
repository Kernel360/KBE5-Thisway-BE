package org.thisway.log;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.log.dto.request.LogDataBatchRequest;
import org.thisway.log.dto.request.LogDataEntry;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogDataSaveService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void saveBatchLogData(LogDataBatchRequest request) {
        Long vehicleId = request.vehicleId();
        Long mdn = request.mdn();
        List<LogDataEntry> entries = request.entries();

        log.info("차량 ID: {}에 대한 로그 데이터 배치 저장 시작 (항목 수: {})", vehicleId, entries.size());

        List<Object[]> powerLogBatch = new ArrayList<>();
        List<Object[]> gpsLogBatch = new ArrayList<>();
        List<Object[]> geofenceLogBatch = new ArrayList<>();

        for (LogDataEntry entry : entries) {
            powerLogBatch.add(new Object[]{
                    vehicleId,
                    mdn,
                    "ON",
                    entry.occurredTime(),
                    entry.gpsStatus(),
                    entry.latitude(),
                    entry.longitude(),
                    entry.totalTripMeter()
            });

            gpsLogBatch.add(new Object[]{
                    vehicleId,
                    mdn,
                    entry.gpsStatus(),
                    entry.latitude(),
                    entry.longitude(),
                    entry.angle(),
                    entry.speed(),
                    entry.totalTripMeter(),
                    entry.batteryVoltage(),
                    entry.occurredTime()
            });

            geofenceLogBatch.add(new Object[]{
                    vehicleId,
                    mdn,
                    entry.occurredTime(),
                    entry.geofenceGroupId(),
                    entry.geofenceId(),
                    entry.eventVal(),
                    entry.gpsStatus(),
                    entry.latitude(),
                    entry.longitude(),
                    entry.angle()
            });
        }

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
        jdbcTemplate.batchUpdate(powerLogSql, powerLogBatch);

        String gpsLogSql = "INSERT INTO gps_log (vehicle_id, mdn, gps_status, latitude, longitude, anger, speed, total_trip_meter, battery_voltage, occurred_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(gpsLogSql, gpsLogBatch);

        String geofenceLogSql = "INSERT INTO geofence_log (vehicle_id, mdn, occured_time, geofence_group_id, geofence_id, event_val, gps_status, latitude, longitude, angle) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(geofenceLogSql, geofenceLogBatch);

        log.info("차량 ID: {}에 대한 로그 데이터 배치 저장 완료", vehicleId);
    }
}
