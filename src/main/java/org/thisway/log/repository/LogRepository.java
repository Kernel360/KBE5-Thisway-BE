package org.thisway.log.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.thisway.log.domain.GeofenceLogData;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.domain.PowerLogData;

@Repository
@RequiredArgsConstructor
public class LogRepository {

    private final JdbcTemplate jdbcTemplate;

    public void savePowerLog(PowerLogData powerLogData) {
        Object[] powerLogParams = new Object[]{
                powerLogData.vehicleId(),
                powerLogData.mdn(),
                powerLogData.powerStatus(),
                powerLogData.powerTime(),
                powerLogData.gpsStatus(),
                powerLogData.latitude(),
                powerLogData.longitude(),
                powerLogData.totalTripMeter()
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
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(powerLogSql, powerLogParams);
    }

    public void saveGpsLogs(List<GpsLogData> gpsLogDataList) {
        List<Object[]> gpsLogBatch = gpsLogDataList.stream()
                .map(this::toGpsLogParams)
                .toList();

        String gpsLogSql = "INSERT INTO gps_log ("
                + "vehicle_id, "
                + "mdn, "
                + "gps_status, "
                + "latitude, "
                + "longitude, "
                + "angle, "
                + "speed, "
                + "total_trip_meter, "
                + "battery_voltage, "
                + "occurred_time"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(gpsLogSql, gpsLogBatch);
    }

    private Object[] toGpsLogParams(GpsLogData gpsLogData) {
        return new Object[]{
                gpsLogData.vehicleId(),
                gpsLogData.mdn(),
                gpsLogData.gpsStatus(),
                gpsLogData.latitude(),
                gpsLogData.longitude(),
                gpsLogData.angle(),
                gpsLogData.speed(),
                gpsLogData.totalTripMeter(),
                gpsLogData.batteryVoltage(),
                gpsLogData.occurredTime()
        };
    }

    public void saveGeofenceLog(GeofenceLogData geofenceLogData) {
        Object[] geofenceLogParams = new Object[]{
                geofenceLogData.vehicleId(),
                geofenceLogData.mdn(),
                geofenceLogData.occurredTime(),
                geofenceLogData.geofenceGroupId(),
                geofenceLogData.geofenceId(),
                geofenceLogData.eventVal(),
                geofenceLogData.gpsStatus(),
                geofenceLogData.latitude(),
                geofenceLogData.longitude(),
                geofenceLogData.angle()
        };

        String geofenceLogSql = "INSERT INTO geofence_log ("
                + "vehicle_id, "
                + "mdn, "
                + "occured_time, "
                + "geofence_group_id, "
                + "geofence_id, "
                + "event_val, "
                + "gps_status, "
                + "latitude, "
                + "longitude, "
                + "angle"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(geofenceLogSql, geofenceLogParams);
    }
}
