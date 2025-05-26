package org.thisway.log;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.repository.VehicleRepository;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogDataGeneratorServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private LogDataGeneratorService logDataGeneratorService;

    @Captor
    private ArgumentCaptor<Object[]> sqlParamsCaptor;

    private List<Vehicle> activeCarList;
    private Vehicle testCar;

    private void setEntityId(Object entity, Long id) {
        try {
            Field idField = entity.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        testCar = Vehicle.builder()
                .carNumber("12가3456")
                .color("빨강")
                .mileage(10000)
                .startCar(true)
                .latitude(37.5665)
                .longitude(126.9780)
                .build();

        setEntityId(testCar, 1L);

        activeCarList = new ArrayList<>();
        activeCarList.add(testCar);
    }

    @Test
    @DisplayName("시동이 켜진 차량이 없을 때 로그 생성 안함")
    void 시동이_켜진_차량이_없을_때_로그_생성_안함() {
        when(vehicleRepository.findByStartCarTrue()).thenReturn(new ArrayList<>());

        logDataGeneratorService.generateAndSaveLogsForActiveVehicles();

        verify(jdbcTemplate, times(0)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("시동이 켜진 차량에 대해 세 종류의 로그 생성")
    void 시동이_켜진_차량에_대해_세_종류의_로그_생성() {
        when(vehicleRepository.findByStartCarTrue()).thenReturn(activeCarList);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        logDataGeneratorService.generateAndSaveLogsForActiveVehicles();

        verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("파워로그 생성 및 저장 검증")
    void 파워로그_생성_및_저장_검증() {
        when(vehicleRepository.findByStartCarTrue()).thenReturn(activeCarList);
        String expectedSql = "INSERT INTO power_log (vehicle_id, mdn, power_status, power_time, gps_status, latitude, longitude, total_trip_meter) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        logDataGeneratorService.generateAndSaveLogsForActiveVehicles();

        verify(jdbcTemplate).update(eq(expectedSql), sqlParamsCaptor.capture());
        Object[] params = sqlParamsCaptor.getValue();

        assertThat(params[0]).isEqualTo(testCar.getId());
        assertThat(params[2]).isEqualTo("ON");
        assertThat(params[3]).isInstanceOf(LocalDateTime.class);
        assertThat(params[4]).isEqualTo("A");
        assertThat(params[5]).isEqualTo(testCar.getLatitude());
        assertThat(params[6]).isEqualTo(testCar.getLongitude());
        assertThat((Integer)params[7]).isGreaterThanOrEqualTo(testCar.getMileage());  // total_trip_meter
    }

    @Test
    @DisplayName("GPS로그 생성 및 저장 검증")
    void GPS로그_생성_및_저장_검증() {
        when(vehicleRepository.findByStartCarTrue()).thenReturn(activeCarList);
        String expectedSql = "INSERT INTO gps_log (vehicle_id, mdn, gps_status, latitude, longitude, anger, speed, total_trip_meter, battery_voltage, occured_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        logDataGeneratorService.generateAndSaveLogsForActiveVehicles();

        verify(jdbcTemplate).update(eq(expectedSql), sqlParamsCaptor.capture());
        Object[] params = sqlParamsCaptor.getValue();

        assertThat(params[0]).isEqualTo(testCar.getId());  // vehicle_id
        assertThat(params[2]).isEqualTo("A");  // gps_status
        assertThat(params[3]).isEqualTo(testCar.getLatitude());  // latitude
        assertThat(params[4]).isEqualTo(testCar.getLongitude());  // longitude
        assertThat((Integer)params[5]).isLessThan(360);  // anger
        assertThat((Integer)params[6]).isLessThan(120);  // speed
        assertThat((Integer)params[7]).isGreaterThanOrEqualTo(testCar.getMileage());  // total_trip_meter
        assertThat((Integer)params[8]).isBetween(11000, 14000);  // battery_voltage
        assertThat(params[9]).isInstanceOf(LocalDateTime.class);  // occured_time
    }

    @Test
    @DisplayName("지오펜스로그 생성 및 저장 검증")
    void 지오펜스로그_생성_및_저장_검증() {
        when(vehicleRepository.findByStartCarTrue()).thenReturn(activeCarList);
        String expectedSql = "INSERT INTO geofence_log (vehicle_id, mdn, occured_time, geofence_group_id, geofence_id, event_val, gps_status, latitude, longitude, anger) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        logDataGeneratorService.generateAndSaveLogsForActiveVehicles();

        verify(jdbcTemplate).update(eq(expectedSql), sqlParamsCaptor.capture());
        Object[] params = sqlParamsCaptor.getValue();

        assertThat(params[0]).isEqualTo(testCar.getId());
        assertThat(params[2]).isInstanceOf(LocalDateTime.class);
        assertThat((Long)params[3]).isBetween(1000L, 1010L);
        assertThat((Long)params[4]).isBetween(2000L, 2100L);
        assertThat((Byte)params[5]).isBetween((byte)0, (byte)2);
        assertThat(params[6]).isEqualTo("A");
        assertThat(params[7]).isEqualTo(testCar.getLatitude());
        assertThat(params[8]).isEqualTo(testCar.getLongitude());
        assertThat((Integer)params[9]).isLessThan(360);
    }
}
