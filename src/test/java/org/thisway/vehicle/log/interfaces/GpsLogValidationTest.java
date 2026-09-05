package org.thisway.vehicle.log.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.common.GlobalExceptionHandler;
import org.thisway.emulator.infrastructure.EmulatorRepository;
import org.thisway.vehicle.log.application.GpsLogSaveService;
import org.thisway.vehicle.log.application.GpsLogService;
import org.thisway.vehicle.log.application.LogService;
import org.thisway.vehicle.log.infrastructure.LogRepository;
import org.thisway.vehicle.log.util.LogDataConverter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GpsLogValidationTest {
    private static final GpsLogEntry VALID = entry(null, "30", "37000000");

    static Stream<GpsLogRequest> invalidRequests() {
        return Stream.of(
                request("202609051200", "1", null),
                request("202609051200", "0", List.of()),
                request("202609051200", "2", List.of(VALID)),
                request("202609051200", "601", Collections.nCopies(601, VALID)),
                request("202602301200", "1", List.of(VALID)),
                request("202609051260", "1", List.of(VALID)),
                request(null, "1", List.of(VALID)),
                request("202609051200", "1", Collections.singletonList(null)),
                request("202609051200", "1", List.of(entry("60", "0", "0"))),
                request("202609051200", "1", List.of(entry(null, "60", "0"))),
                request("202609051200", "1", List.of(entry(null, "0", "NaN"))),
                request("202609051200", "1", List.of(entry(null, "0", "Infinity"))),
                request("202609051200", "1", List.of(entry(null, "0", "90000001"))),
                request("202609051200", "1", List.of(new GpsLogEntry("0", "0", "?", "0", "0", "0", "0", "0", "0"))),
                request("202609051200", "1", List.of(new GpsLogEntry("0", "0", "A", "0", "0", "0", "0", "2147483648", "0")))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void 잘못된_HTTP_packet은_저장이나_publish_전에_400이다(GpsLogRequest request) throws Exception {
        GpsLogService service = mock(GpsLogService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new LogController(mock(LogService.class), service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/logs/gps").contentType("application/json")
                .content(new ObjectMapper().writeValueAsString(request))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 정상_최대_packet과_초단위_시각을_허용한다() {
        GpsLogRequestValidator.validate(request("20260905120030", "600", Collections.nCopies(600, VALID)));
        GpsLogRequestValidator.validate(request("202402291200", "1", List.of(VALID)));
    }

    @Test
    void 정상_HTTP_packet은_서비스에_전달한다() throws Exception {
        GpsLogService service = mock(GpsLogService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new LogController(mock(LogService.class), service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        GpsLogRequest request = request("202609051200", "1", List.of(VALID));
        mvc.perform(post("/api/logs/gps").contentType("application/json")
                .content(new ObjectMapper().writeValueAsString(request))).andExpect(status().isOk());
        verify(service).saveGpsLog(request);
    }

    @Test
    void consumer_저장경계도_조회와_쓰기_전에_거부한다() {
        EmulatorRepository emulators = mock(EmulatorRepository.class);
        LogRepository logs = mock(LogRepository.class);
        GpsLogSaveService service = new GpsLogSaveService(emulators, logs, new LogDataConverter());
        assertThatThrownBy(() -> service.saveGpsLog(request("invalid", "1", List.of(VALID))))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(emulators, logs);
    }

    private static GpsLogRequest request(String time, String count, List<GpsLogEntry> entries) {
        return new GpsLogRequest("01234567890", "A001", "6", "5", "1", time, count, entries);
    }

    private static GpsLogEntry entry(String minute, String second, String latitude) {
        return new GpsLogEntry(minute, second, "A", latitude, "127000000", "90", "20", "1000", "12");
    }
}
