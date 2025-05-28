package org.thisway.log.repository;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LogRepository {
    
    private final JdbcTemplate jdbcTemplate;

    public void savePowerLog(
            Long vehicleId,
            String mdn,
            boolean powerStatus,
            LocalDateTime powerTime,
            String gpsStatus,
            Double latitude,
            Double longitude,
            Integer totalTripMeter
    ) {
        Object[] powerLogParams = new Object[]{
                vehicleId,
                mdn,
                powerStatus,
                powerTime,
                gpsStatus,
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
    }

    public void saveGpsLogs(List<Object[]> gpsLogBatch) {
        String gpsLogSql = "INSERT INTO gps_log (vehicle_id, mdn, gps_status, latitude, longitude, anger, speed, total_trip_meter, battery_voltage, occurred_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(gpsLogSql, gpsLogBatch);
    }

    public void saveGeofenceLog(
            Long vehicleId,
            String mdn,
            LocalDateTime occurredTime,
            Long geofenceGroupId,
            Long geofenceId,
            Byte eventVal,
            String gpsStatus,
            Double latitude,
            Double longitude,
            Integer angle
    ) {
        Object[] geofenceLogParams = new Object[]{
                vehicleId,
                mdn,
                occurredTime,
                geofenceGroupId,
                geofenceId,
                eventVal,
                gpsStatus,
                latitude,
                longitude,
                angle
        };
        
        String geofenceLogSql = "INSERT INTO geofence_log (vehicle_id, mdn, occured_time, geofence_group_id, geofence_id, event_val, gps_status, latitude, longitude, angle) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(geofenceLogSql, geofenceLogParams);
    }
}
