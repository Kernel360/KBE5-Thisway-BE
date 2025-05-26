package org.thisway.log;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.repository.VehicleRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogDataGeneratorService {

    private final JdbcTemplate jdbcTemplate;
    private final VehicleRepository vehicleRepository;
    private final Random random = new Random();

    private static final String FIXED_GPS_STATUS = "A";

    /**
     * 시동이 켜진 차량에 대한 로그 데이터 생성 및 저장
     */
    public void generateAndSaveLogsForActiveVehicles() {
        List<Vehicle> activeVehicles = vehicleRepository.findByStartCarTrue();
        
        if (activeVehicles.isEmpty()) {
            log.info("시동이 켜진 차량이 없습니다.");
            return;
        }
        
        log.info("시동이 켜진 차량 수: {}", activeVehicles.size());
        
        for (Vehicle vehicle : activeVehicles) {
            generateAndSavePowerLog(vehicle);
            generateAndSaveGpsLog(vehicle);
            generateAndSaveGeofenceLog(vehicle);
        }
    }

    //PowerLog
    private void generateAndSavePowerLog(Vehicle vehicle) {
        Long vehicleId = vehicle.getId();
        Long mdn = generateRandomMdn();
        String powerStatus = "ON";
        LocalDateTime powerTime = LocalDateTime.now();
        String gpsStatus = FIXED_GPS_STATUS;
        
        Double latitude = null;
        Double longitude = null;
        if (gpsStatus.equals("A")) {
            latitude = vehicle.getLatitude() != null ? vehicle.getLatitude() : generateRandomLatitude();
            longitude = vehicle.getLongitude() != null ? vehicle.getLongitude() : generateRandomLongitude();
        }
        
        Integer totalTripMeter = vehicle.getMileage() + random.nextInt(100);
        
        String sql = "INSERT INTO power_log (vehicle_id, mdn, power_status, power_time, gps_status, latitude, longitude, total_trip_meter) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql, vehicleId, mdn, powerStatus, powerTime, gpsStatus, latitude, longitude, totalTripMeter);
        log.info("차량 ID: {}에 대한 PowerLog 생성 완료", vehicle.getId());
    }

    //GpsLog
    private void generateAndSaveGpsLog(Vehicle vehicle) {
        Long vehicleId = vehicle.getId();
        Long mdn = generateRandomMdn();
        String gpsStatus = FIXED_GPS_STATUS;

        Double latitude = null;
        Double longitude = null;
        if (gpsStatus.equals("A")) {
            latitude = vehicle.getLatitude() != null ? vehicle.getLatitude() : generateRandomLatitude();
            longitude = vehicle.getLongitude() != null ? vehicle.getLongitude() : generateRandomLongitude();
        }
        
        Integer anger = random.nextInt(360);
        Integer speed = random.nextInt(120);
        Integer totalTripMeter = vehicle.getMileage() + random.nextInt(100);
        Integer batteryVoltage = 11000 + random.nextInt(3000); // 11V ~ 14V (밀리볼트 단위)
        LocalDateTime occuredTime = LocalDateTime.now();
        
        String sql = "INSERT INTO gps_log (vehicle_id, mdn, gps_status, latitude, longitude, anger, speed, total_trip_meter, battery_voltage, occured_time) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql, vehicleId, mdn, gpsStatus, latitude, longitude, anger, speed, totalTripMeter, batteryVoltage, occuredTime);
        log.info("차량 ID: {}에 대한 GpsLog 생성 완료", vehicle.getId());
    }

    //GeofenceLog
    private void generateAndSaveGeofenceLog(Vehicle vehicle) {
        Long vehicleId = vehicle.getId();
        Long mdn = generateRandomMdn();
        LocalDateTime occuredTime = LocalDateTime.now();
        Long geofenceGroupId = 1000L + random.nextInt(10);
        Long geofenceId = 2000L + random.nextInt(100);
        Byte eventVal = (byte) random.nextInt(3);
        String gpsStatus = "A";
        
        Double latitude = vehicle.getLatitude() != null ? vehicle.getLatitude() : generateRandomLatitude();
        Double longitude = vehicle.getLongitude() != null ? vehicle.getLongitude() : generateRandomLongitude();
        Integer anger = random.nextInt(360);
        
        String sql = "INSERT INTO geofence_log (vehicle_id, mdn, occured_time, geofence_group_id, geofence_id, event_val, gps_status, latitude, longitude, anger) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql, vehicleId, mdn, occuredTime, geofenceGroupId, geofenceId, eventVal, gpsStatus, latitude, longitude, anger);
        log.info("차량 ID: {}에 대한 GeofenceLog 생성 완료", vehicle.getId());
    }

    private Long generateRandomMdn() {
        return 1000000000L + random.nextInt(900000000);
    }
    
    private Double generateRandomLatitude() {
        return 35.0 + (random.nextDouble() * 3);
    }
    
    private Double generateRandomLongitude() {
        return 126.0 + (random.nextDouble() * 3);
    }
}
