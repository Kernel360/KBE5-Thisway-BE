package org.thisway.log.repository;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.thisway.log.domain.GeofenceLogData;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.domain.GpsStatus;
import org.thisway.log.domain.PowerLogData;

@ExtendWith(MockitoExtension.class)
public class LogRepositoryTest {

    private static final Long VEHICLE_ID = 1L;
    private static final String MDN = "01234567890";
    @Mock
    private JdbcTemplate jdbcTemplate;
    @InjectMocks
    private LogRepository logRepository;

    @Test
    @DisplayName("GPS 로그 저장 테스트")
    void GPS_로그_배치_저장_테스트() {
        List<GpsLogData> gpsLogDataList = Arrays.asList(
                new GpsLogData(
                        VEHICLE_ID,
                        MDN,
                        GpsStatus.NORMAL,
                        41.40338,
                        2.17403,
                        270,
                        100,
                        10000,
                        100,
                        LocalDateTime.now()
                ),
                new GpsLogData(
                        VEHICLE_ID,
                        MDN,
                        GpsStatus.NORMAL,
                        41.40338,
                        2.17403,
                        270,
                        100,
                        10000,
                        100,
                        LocalDateTime.now()
                )
        );

        logRepository.saveGpsLogs(gpsLogDataList);
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("시동 로그 저장 테스트")
    void 시동_로그_저장_테스트() {
        PowerLogData powerLogData = new PowerLogData(
                VEHICLE_ID,
                MDN,
                true,
                LocalDateTime.now(),
                GpsStatus.NORMAL,
                41.40338,
                2.17403,
                10000
        );

        logRepository.savePowerLog(powerLogData);
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("지오펜스 로그 저장 테스트")
    void 지오펜스_로그_저장_테스트() {
        GeofenceLogData geofenceLogData = new GeofenceLogData(
                VEHICLE_ID,
                MDN,
                LocalDateTime.now(),
                1L,
                1L,
                (byte) 1,
                GpsStatus.NORMAL,
                41.40338,
                2.17403,
                200
        );

        logRepository.saveGeofenceLog(geofenceLogData);
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }
}
