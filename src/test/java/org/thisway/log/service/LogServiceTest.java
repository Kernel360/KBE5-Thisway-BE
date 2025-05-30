package org.thisway.log.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.emulator.entity.Emulator;
import org.thisway.emulator.repository.EmulatorRepository;
import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;
import org.thisway.log.dto.request.gpsLog.GpsLogEntry;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;
import org.thisway.vehicle.entity.Vehicle;

@ExtendWith(MockitoExtension.class)
public class LogServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Emulator emulator;

    @Mock
    private Vehicle vehicle;

    @Mock
    private EmulatorRepository emulatorRepository;

    @InjectMocks
    private LogService logService;

    private static final String VALID_MDN = "01234567890";
    private static final String INVALID_MDN = "012345678900";
    private static final Long VEHICLE_ID = 1L;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Nested
    @DisplayName("유틸 메서드 테스트")
    class UtilMethodTest {

        @Test
        @DisplayName("좌표 변환 테스트")
        void 좌표_파싱이_올바르게_실행되어야한다() throws Exception {
            String coordinate = "4140338";
            Double expected = 41.40338;

            Double result = ReflectionTestUtils.invokeMethod(logService, "parseCoordinate", coordinate);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("날짜 시간 변환 테스트")
        void 날짜_파싱이_올바르게_실행되어야한다() throws Exception {
            String dateTime = "202109010920";
            String seconds = "33";
            LocalDateTime expected = LocalDateTime.parse("20210901092033", DATE_TIME_FORMATTER);

            LocalDateTime result = ReflectionTestUtils.invokeMethod(logService, "parseDateTime", dateTime, seconds);

            assertThat(expected).isEqualTo(result);
        }

        @Test
        @DisplayName("유효한 MDN으로 차량 ID 조회 테스트")
        void 유효한_MDN이면_차량ID를_반환해야한다() throws Exception {
            setupMocks();

            Long result = ReflectionTestUtils.invokeMethod(logService, "getVehicleIdByMdn", Long.parseLong(VALID_MDN));

            assertThat(result).isEqualTo(VEHICLE_ID);
        }

        @Test
        @DisplayName("유효하지 않은 MDN으로 차량 ID 조회시 예외 발생")
        void 유효하지_않은_MDN이면_예외를_발생시킨다() {
            when(emulatorRepository.findByMdn(Long.parseLong(INVALID_MDN)))
                    .thenReturn(Optional.empty());
            assertThatExceptionOfType(CustomException.class).isThrownBy(() -> ReflectionTestUtils.invokeMethod(
                            logService, "getVehicleIdByMdn", Long.parseLong(INVALID_MDN)))
                    .extracting(CustomException::getErrorCode)
                    .isEqualTo(ErrorCode.EMULATOR_NOT_FOUND);
        }
    }

    private void setupMocks() {
        when(emulatorRepository.findByMdn(Long.parseLong(VALID_MDN))).thenReturn(java.util.Optional.of(emulator));
        when(emulator.getVehicle()).thenReturn(vehicle);
        when(vehicle.getId()).thenReturn(VEHICLE_ID);
    }

    private GpsLogRequest createValidGpsLogRequest(String mdn, int entryCount) {
        List<GpsLogEntry> entries = new ArrayList<>();
        for (int i = 0; i < entryCount; i++) {
            entries.add(new GpsLogEntry(
                    "33",
                    "A",
                    "4140338",
                    "217403",
                    "270",
                    "100",
                    "10000",
                    "100"
            ));
        }
        return new GpsLogRequest(
                mdn,
                "A001",
                "6",
                "5",
                "1",
                "202109010920",
                String.valueOf(entryCount),
                entries
        );
    }

    private PowerLogRequest createValidPowerLogRequest() {
        return new PowerLogRequest(
                VALID_MDN,
                "A001",
                "5",
                "1",
                "20210901092000",
                "",
                "A",
                "4140338",
                "217403",
                "270",
                "0",
                "10000"
        );
    }

    private GeofenceLogRequest createValidGeofenceLogRequest() {
        return new GeofenceLogRequest(
                VALID_MDN,
                "A001",
                "6",
                "5",
                "1",
                "20210901174045",
                "1",
                "1",
                "1",
                "A",
                "4140338",
                "217403",
                "200",
                "0",
                "11000"
        );
    }

    @Nested
    @DisplayName("GPS 로그 정보 저장 테스트")
    class SaveGpsLogTest {

        @Test
        @DisplayName("유효한 요청 시 GPS 로그 저장 성공 테스트")
        void 유효한_요청이라면_GPS로그를_저장해야한다(){
            GpsLogRequest request = createValidGpsLogRequest(VALID_MDN, 1);
            setupMocks();

            logService.saveGpsLog(request);

            verify(jdbcTemplate).batchUpdate(anyString(), anyList());;
        }

        @Test
        @DisplayName("다중 엔트리가 있는 GPS 로그 저장 테스트")
        void GPS로그의_엔트리가_여러_개일_때_저장할_수_있어야한다() {
            int entryCount = 10;
            GpsLogRequest request = createValidGpsLogRequest(VALID_MDN, entryCount);
            setupMocks();

            ArgumentCaptor<List<Object[]>> batchArgsCaptor = ArgumentCaptor.forClass((Class)List.class);

            logService.saveGpsLog(request);

            verify(jdbcTemplate).batchUpdate(anyString(), batchArgsCaptor.capture());
            List<Object[]> capturedBatchArgs = batchArgsCaptor.getValue();
            assertThat(capturedBatchArgs.size()).isEqualTo(entryCount);
        }
    }

    @Nested
    @DisplayName("시동 정보 저장 테스트")
    class SavePowerLogTest {

        @Test
        @DisplayName("유효한 요청 시 시동 로그 저장 성공 테스트")
        void 유효한_요청이라면_시동로그를_저장해야한다() {
            PowerLogRequest request = createValidPowerLogRequest();
            setupMocks();

            logService.savePowerLog(request);

            verify(jdbcTemplate).update(anyString(), any(Object[].class));
        }
    }

    @Nested
    @DisplayName("지오펜스 정보 저장 테스트")
    class SaveGeofenceLogTest {
        @Test
        @DisplayName("유효한 요청 시 지오펜스 로그 저장 성공 테스트")
        void 유효한_요청이라면_지오펜스로그를_저장해야한다() {
            GeofenceLogRequest request = createValidGeofenceLogRequest();
            setupMocks();

            logService.saveGeofenceLog(request);

            verify(jdbcTemplate).update(anyString(), any(Object[].class));
        }
    }
}
