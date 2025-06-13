package org.thisway.log.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.thisway.log.domain.GeofenceLogData;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.domain.GpsStatus;
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
                powerLogData.gpsStatus().getCode(),
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
                gpsLogData.gpsStatus().getCode(),
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
                geofenceLogData.gpsStatus().getCode(),
                geofenceLogData.latitude(),
                geofenceLogData.longitude(),
                geofenceLogData.angle()
        };

        String geofenceLogSql = "INSERT INTO geofence_log ("
                + "vehicle_id, "
                + "mdn, "
                + "occurred_time, "
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

    public List<PowerLogData> findPowerLogByVehicleId(Long vehicleId) {
        String sql = "SELECT vehicle_id, mdn, power_status, power_time, gps_status, latitude, longitude, total_trip_meter "
                + "FROM power_log "
                + "WHERE vehicle_id = ? "
                + "ORDER BY power_time";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new PowerLogData(
                        rs.getLong("vehicle_id"),
                        rs.getString("mdn"),
                        rs.getBoolean("power_status"),
                        rs.getTimestamp("power_time").toLocalDateTime(),
                        GpsStatus.fromCode(rs.getString("gps_status")),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getInt("total_trip_meter")
                ), vehicleId
        );
    }

    public GpsLogData findCurrentGpsByVehicleId(Long vehicleId) {
        String sql = "SELECT vehicle_id, mdn, gps_status, latitude, longitude, angle, speed, total_trip_meter, battery_voltage, occurred_time "
                + "FROM gps_log "
                + "WHERE vehicle_id = ? "
                + "ORDER BY occurred_time DESC limit 1";

        return jdbcTemplate.queryForObject(sql,
                (rs, rowNum) -> new GpsLogData(
                        rs.getLong("vehicle_id"),
                        rs.getString("mdn"),
                        GpsStatus.fromCode(rs.getString("gps_status")),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getInt("angle"),
                        rs.getInt("speed"),
                        rs.getInt("total_trip_meter"),
                        rs.getInt("battery_voltage"),
                        rs.getTimestamp("occurred_time").toLocalDateTime()
                ), vehicleId
        );
    }

}
