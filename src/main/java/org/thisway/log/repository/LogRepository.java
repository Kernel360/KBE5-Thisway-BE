package org.thisway.log.repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.thisway.log.domain.GeofenceLogData;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.domain.GpsStatus;
import org.thisway.log.domain.PowerLogData;

import java.util.HashMap;

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

    public List<PowerLogData> findPowerLogsByVehicleId(Long vehicleId) {
        String sql =
                "SELECT vehicle_id, mdn, power_status, power_time, gps_status, latitude, longitude, total_trip_meter "
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

    public List<PowerLogData> findAllPowerLogs() {
        String sql =
                "SELECT vehicle_id, mdn, power_status, power_time, gps_status, latitude, longitude, total_trip_meter "
                        + "FROM power_log "
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
                )
        );
    }

    public PowerLogData findOnLogByVehicleIdAndPowerTime(Long vehicleId, LocalDateTime powerTime) {
        String sql = "SELECT vehicle_id, mdn, power_status, power_time, gps_status, latitude, longitude, total_trip_meter "
                + "FROM power_log "
                + "WHERE vehicle_id = ? AND power_time = ?";

        Object[] params = new Object[]{ vehicleId, powerTime };

        return jdbcTemplate.queryForObject(sql,
                (rs, rowNum) -> new PowerLogData(
                        rs.getLong("vehicle_id"),
                        rs.getString("mdn"),
                        rs.getBoolean("power_status"),
                        rs.getTimestamp("power_time").toLocalDateTime(),
                        GpsStatus.fromCode(rs.getString("gps_status")),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getInt("total_trip_meter")
                ), params
        );
    }

    public List<PowerLogData> findPowerLogsByVehicleIdAndPowerTime(Long vehicleId, LocalDateTime start) {
        String sql =
                "SELECT vehicle_id, mdn, power_status, power_time, gps_status, latitude, longitude, total_trip_meter "
                        + "FROM power_log "
                        + "WHERE vehicle_id = ? AND power_time >= ? "
                        + "ORDER BY power_time "
                        + "LIMIT 2";

        Object[] params = new Object[]{vehicleId, start};

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
                ), params
        );
    }

    public Map<Long, GpsLogData> findCurrentGpsByVehicleIds(List<Long> vehicleIds) {
        if (vehicleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String inClause = vehicleIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));

        String sql = String.format("""
                SELECT gl.*
                FROM gps_log gl
                JOIN (
                    SELECT vehicle_id, MAX(occurred_time) AS latest_time
                    FROM gps_log
                    WHERE vehicle_id IN (%s)
                    GROUP BY vehicle_id
                ) latest ON gl.vehicle_id = latest.vehicle_id AND gl.occurred_time = latest.latest_time
                """, inClause);

        List<GpsLogData> gpsList = jdbcTemplate.query(sql,
                vehicleIds.toArray(),
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
                )
        );

        return gpsList.stream().collect(Collectors.toMap(GpsLogData::vehicleId, gps -> gps));
    }

    public GpsLogData getCurrentGpsByVehicleId(Long vehicleId) {
        String sql =
                "SELECT vehicle_id, mdn, gps_status, latitude, longitude, angle, speed, total_trip_meter, battery_voltage, occurred_time "
                        + "FROM gps_log "
                        + "WHERE vehicle_id = ? "
                        + "ORDER BY occurred_time DESC "
                        + "LIMIT 1";

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

    public List<GpsLogData> findGpsLogsByVehicleId(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime) {
        String sql =
                "SELECT vehicle_id, mdn, gps_status, latitude, longitude, angle, speed, total_trip_meter, battery_voltage, occurred_time "
                        + "FROM gps_log "
                        + "WHERE vehicle_id = ? AND occurred_time > ? AND occurred_time <= ? "
                        + "ORDER BY occurred_time";

        Object[] params = new Object[]{
                vehicleId,
                startTime,
                endTime
        };

        return jdbcTemplate.query(sql,
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
                ), params
        );
    }

    /**
     * 특정 회사의 특정 날짜에 대해 시간대별 GPS 로그 개수를 반환
     * @param companyId 회사 ID
     * @param startDateTime 시작 날짜시간 (해당 날짜 00:00:00)
     * @param endDateTime 종료 날짜시간 (해당 날짜 23:59:59)
     * @return Map<시간대(0~23), GPS 로그 개수>
     */
    public Map<Integer, Long> countGpsLogsByCompanyAndHour(Long companyId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        // 디버깅을 위한 로그 추가
        System.out.println("=== GPS 로그 조회 디버깅 ===");
        System.out.println("회사 ID: " + companyId);
        System.out.println("시작 시간: " + startDateTime);
        System.out.println("종료 시간: " + endDateTime);
        
        // 먼저 해당 회사의 차량 수 확인
        String vehicleCountSql = "SELECT COUNT(*) FROM vehicle WHERE company_id = ? AND active = true";
        Long vehicleCount = jdbcTemplate.queryForObject(vehicleCountSql, Long.class, companyId);
        System.out.println("해당 회사 차량 수: " + vehicleCount);
        
        // GPS 로그가 있는지 먼저 확인
        String gpsLogCheckSql = """
            SELECT COUNT(*) 
            FROM gps_log gl
            JOIN vehicle v ON gl.vehicle_id = v.id
            WHERE v.company_id = ? 
            AND gl.occurred_time >= ? 
            AND gl.occurred_time <= ?
            AND v.active = true
            """;
        Long totalGpsLogs = jdbcTemplate.queryForObject(gpsLogCheckSql, Long.class, companyId, startDateTime, endDateTime);
        System.out.println("전체 GPS 로그 수: " + totalGpsLogs);
        
        if (totalGpsLogs == 0) {
            System.out.println("GPS 로그가 없습니다!");
            return new HashMap<>();
        }
        
        // 시간대별 GPS 로그 개수 조회 (DB 호환성을 위해 EXTRACT 사용)
        String sql = """
            SELECT EXTRACT(HOUR FROM gl.occurred_time) as hour, COUNT(*) as count
            FROM gps_log gl
            JOIN vehicle v ON gl.vehicle_id = v.id
            WHERE v.company_id = ? 
            AND gl.occurred_time >= ? 
            AND gl.occurred_time <= ?
            AND v.active = true
            GROUP BY EXTRACT(HOUR FROM gl.occurred_time)
            ORDER BY hour
            """;

        Object[] params = new Object[]{companyId, startDateTime, endDateTime};

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params);
        System.out.println("시간대별 결과: " + results);
        
        Map<Integer, Long> hourlyCounts = results.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row.get("hour")).intValue(),
                        row -> ((Number) row.get("count")).longValue()
                ));
        
        System.out.println("시간대별 카운트: " + hourlyCounts);
        System.out.println("=== GPS 로그 조회 디버깅 완료 ===");
        
        return hourlyCounts;
    }

}
