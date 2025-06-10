package org.thisway.log.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import org.thisway.emulator.entity.Emulator;
import org.thisway.emulator.repository.EmulatorRepository;
import org.thisway.log.converter.LogDataConverter;
import org.thisway.log.domain.GeofenceLogData;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.domain.PowerLogData;
import org.thisway.log.dto.request.geofenceLog.GeofenceLogRequest;
import org.thisway.log.dto.request.gpsLog.GpsLogEntry;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.dto.request.powerLog.PowerLogRequest;
import org.thisway.log.repository.LogRepository;
import org.thisway.vehicle.entity.Vehicle;

@ExtendWith(MockitoExtension.class)
public class LogServiceTest {

    @Mock
    private LogRepository logRepository;

    @Mock
    private LogDataConverter converter;

    @Mock
    private Emulator emulator;

    @Mock
    private Vehicle vehicle;

    @Mock
    private EmulatorRepository emulatorRepository;

    @InjectMocks
    private LogService logService;

    private static final String VALID_MDN = "01234567890";
    private static final Long VEHICLE_ID = 1L;

    private void setupMocks() {
        when(emulatorRepository.findByMdn(VALID_MDN)).thenReturn(Optional.of(emulator));
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
                    "100"));
        }
        return new GpsLogRequest(
                mdn,
                "A001",
                "6",
                "5",
                "1",
                "202109010920",
                String.valueOf(entryCount),
                entries);
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
                "10000");
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
                "11000");
    }

    @Nested
    @DisplayName("GPS 로그 정보 저장 테스트")
    class SaveGpsLogTest {

        @Test
        @DisplayName("유효한 요청 시 GPS 로그 저장 성공 테스트")
        void 유효한_요청이라면_GPS로그를_저장해야한다() {
            GpsLogRequest request = createValidGpsLogRequest(VALID_MDN, 1);
            setupMocks();
            LocalDateTime occurredTime = LocalDateTime.of(2021, 9, 1, 9, 20);
            when(converter.convertDateTime(anyString())).thenReturn(occurredTime);

            logService.saveGpsLog(request);
            verify(logRepository).saveGpsLogs(anyList());
        }

        @Test
        @DisplayName("다중 엔트리가 있는 GPS 로그 저장 테스트")
        void GPS로그의_엔트리가_여러_개일_때_저장할_수_있어야한다() {
            int entryCount = 10;
            GpsLogRequest request = createValidGpsLogRequest(VALID_MDN, entryCount);
            setupMocks();
            LocalDateTime occurredTime = LocalDateTime.of(2021, 9, 1, 9, 20);
            when(converter.convertDateTime(anyString())).thenReturn(occurredTime);

            logService.saveGpsLog(request);
            ArgumentCaptor<List<GpsLogData>> gpsLogDataListCaptor = ArgumentCaptor.forClass((Class) List.class);
            verify(logRepository).saveGpsLogs(gpsLogDataListCaptor.capture());
            List<GpsLogData> capturedGpsLogDataList = gpsLogDataListCaptor.getValue();
            assertThat(capturedGpsLogDataList.size()).isEqualTo(entryCount);
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
            LocalDateTime powerTime = LocalDateTime.of(2021, 9, 1, 9, 20, 0);
            when(converter.convertDateTimeWithSec(anyString())).thenReturn(powerTime);

            logService.savePowerLog(request);
            verify(logRepository).savePowerLog(any(PowerLogData.class));
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
            LocalDateTime occurredTime = LocalDateTime.of(2021, 9, 1, 17, 40, 45);
            when(converter.convertDateTimeWithSec(anyString())).thenReturn(occurredTime);

            logService.saveGeofenceLog(request);

            verify(logRepository).saveGeofenceLog(any(GeofenceLogData.class));
        }
    }
}
