package org.thisway.vehicle.log.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thisway.emulator.domain.Emulator;
import org.thisway.emulator.infrastructure.EmulatorRepository;
import org.thisway.vehicle.log.util.LogDataConverter;
import org.thisway.vehicle.log.domain.GpsLogData;
import org.thisway.vehicle.log.interfaces.GpsLogEntry;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.vehicle.log.infrastructure.LogRepository;
import org.thisway.vehicle.domain.Vehicle;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GpsLogSaveServiceTest {

    private static final String VALID_MDN = "01234567890";
    private static final Long VEHICLE_ID = 1L;

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
    private GpsLogSaveService gpsLogSaveService;

    private void setupMocks() {
        when(emulatorRepository.findByMdn(VALID_MDN)).thenReturn(Optional.of(emulator));
        when(emulator.getVehicle()).thenReturn(vehicle);
        when(vehicle.getId()).thenReturn(VEHICLE_ID);
    }

    private GpsLogRequest createValidGpsLogRequest(String mdn, int entryCount) {
        List<GpsLogEntry> entries = new ArrayList<>();
        for (int i = 0; i < entryCount; i++) {
            entries.add(new GpsLogEntry(
                    "20",
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

    @Test
    @DisplayName("유효한 요청 시 GPS 로그 저장 성공 테스트")
    void 유효한_요청이라면_GPS로그를_저장해야한다() {
        GpsLogRequest request = createValidGpsLogRequest(VALID_MDN, 1);
        setupMocks();
        LocalDateTime occurredTime = LocalDateTime.of(2021, 9, 1, 9, 20);
        when(converter.convertDateTime(anyString())).thenReturn(occurredTime);
        when(converter.convertToInteger("20")).thenReturn(20);
        when(converter.convertToInteger("33")).thenReturn(33);

        gpsLogSaveService.saveGpsLog(request);
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
        when(converter.convertToInteger("20")).thenReturn(20);
        when(converter.convertToInteger("33")).thenReturn(33);

        gpsLogSaveService.saveGpsLog(request);
        ArgumentCaptor<List<GpsLogData>> gpsLogDataListCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(logRepository).saveGpsLogs(gpsLogDataListCaptor.capture());
        List<GpsLogData> capturedGpsLogDataList = gpsLogDataListCaptor.getValue();
        assertThat(capturedGpsLogDataList.size()).isEqualTo(entryCount);
    }

    @Test
    @DisplayName("분과 초 필드를 사용한 시간 처리 테스트")
    void 분과_초_필드를_사용하여_정확한_시간을_계산해야한다() {
        GpsLogRequest request = createValidGpsLogRequest(VALID_MDN, 1);
        setupMocks();

        LocalDateTime baseTime = LocalDateTime.of(2021, 9, 1, 9, 0);
        when(converter.convertDateTime(anyString())).thenReturn(baseTime);

        when(converter.convertToInteger("20")).thenReturn(20);
        when(converter.convertToInteger("33")).thenReturn(33);

        gpsLogSaveService.saveGpsLog(request);

        ArgumentCaptor<List<GpsLogData>> gpsLogDataListCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(logRepository).saveGpsLogs(gpsLogDataListCaptor.capture());

        List<GpsLogData> capturedList = gpsLogDataListCaptor.getValue();
        assertThat(capturedList).hasSize(1);

        GpsLogData capturedData = capturedList.get(0);
        LocalDateTime expectedTime = LocalDateTime.of(2021, 9, 1, 9, 20, 33);
        assertThat(capturedData.occurredTime()).isEqualTo(expectedTime);
    }
}
