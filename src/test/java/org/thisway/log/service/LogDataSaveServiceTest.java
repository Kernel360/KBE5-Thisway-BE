package org.thisway.log.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.thisway.log.LogDataSaveService;
import org.thisway.log.dto.request.LogDataBatchRequest;
import org.thisway.log.dto.request.LogDataEntry;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogDataSaveServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LogDataSaveService logDataSaveService;

    private LogDataBatchRequest request;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        
        LogDataEntry entry = new LogDataEntry(
                "A",
                37.5665,
                126.9780,
                90,
                60,
                10000,
                12000,
                now,
                30,
                1L,
                2L,
                (byte) 1
        );

        request = new LogDataBatchRequest(
                1L,
                1234567890L,
                List.of(entry)
        );
    }

    @Test
    @DisplayName("배치 로그 데이터 저장 성공")
    void saveBatchLogData_Success() {
        when(jdbcTemplate.batchUpdate(anyString(), any(List.class))).thenReturn(new int[]{1});

        logDataSaveService.saveBatchLogData(request);

        verify(jdbcTemplate, times(3)).batchUpdate(anyString(), any(List.class));
    }

    @Test
    @DisplayName("예외 발생시 로그 데이터 롤백")
    void saveBatchLogData_RollbackOnException() {
        when(jdbcTemplate.batchUpdate(anyString(), any(List.class)))
                .thenReturn(new int[]{1})
                .thenReturn(new int[]{1})
                .thenThrow(new RuntimeException());

        try {
            logDataSaveService.saveBatchLogData(request);
        } catch (RuntimeException e) {
            verify(jdbcTemplate, times(3)).batchUpdate(anyString(), any(List.class));
        }
    }
}
