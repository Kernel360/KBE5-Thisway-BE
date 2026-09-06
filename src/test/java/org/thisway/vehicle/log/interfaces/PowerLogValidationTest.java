package org.thisway.vehicle.log.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thisway.emulator.infrastructure.EmulatorRepository;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.GlobalExceptionHandler;
import org.thisway.vehicle.application.VehicleService;
import org.thisway.vehicle.log.application.GpsLogService;
import org.thisway.vehicle.log.application.LogServiceImpl;
import org.thisway.vehicle.log.infrastructure.LogRepository;
import org.thisway.vehicle.log.util.LogDataConverter;
import org.thisway.vehicle.triplog.application.TripLogService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PowerLogValidationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-06T01:00:00Z"), ZoneOffset.UTC);

    static Stream<PowerLogRequest> invalidRequests() {
        return Stream.of(
                changed("onTime", "20260230100000"), changed("onTime", "20200101240000"),
                changed("onTime", "20200101100060"), changed("onTime", "202001011000"),
                changed("onTime", "09990101100000"), changed("onTime", ""), changed("onTime", null),
                changed("offTime", " "), changed("offTime", "20200101090000"),
                changed("offTime", "99990101100000"), changed("onTime", "99990101100000"),
                changed("mdn", "x".repeat(21)), changed("mdn", " "), changed("tid", "x".repeat(256)),
                changed("mid", "-1"), changed("pv", "1.5"), changed("did", "2147483648"),
                changed("gcd", "?"), changed("lat", "NaN"), changed("lat", "90000001"),
                changed("lat", "37.5"), changed("lon", "1e8"), changed("lon", "-180000001"),
                changed("sum", "-1"), changed("sum", "2147483648"), changed("sum", null),
                changed("spd", "-1"), changed("ang", "366")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void 직접_서비스와_HTTP_모두_조회나_쓰기_없이_거부한다(PowerLogRequest request) throws Exception {
        var emulators = mock(EmulatorRepository.class);
        var logs = mock(LogRepository.class);
        var converter = mock(LogDataConverter.class);
        var vehicles = mock(VehicleService.class);
        var trips = mock(TripLogService.class);
        var service = new LogServiceImpl(emulators, logs, converter, vehicles, trips);
        assertThatThrownBy(() -> service.savePowerLog(request)).isInstanceOf(CustomException.class);
        var mvc = MockMvcBuilders.standaloneSetup(new LogController(service, mock(GpsLogService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/logs/power").contentType("application/json")
                .content(JSON.writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("10000"));
        verifyNoInteractions(emulators, logs, converter, vehicles, trips);
    }

    @Test
    void 서울시각_5분_경계는_허용하지만_1초_초과는_거부한다() {
        PowerLogRequestValidator.validate(changed("onTime", "20260906100500"), NOW);
        assertThatThrownBy(() -> PowerLogRequestValidator.validate(changed("onTime", "20260906100501"), NOW))
                .isInstanceOf(CustomException.class);
        PowerLogRequestValidator.validate(changed("offTime", "20260906100500"), NOW);
        assertThatThrownBy(() -> PowerLogRequestValidator.validate(changed("offTime", "20260906100501"), NOW))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 과거_지연_윤년_동일시각_종료와_좌표경계를_허용한다() {
        for (var request : java.util.List.of(changed("onTime", "20240229100000"),
                changed("onTime", "10000101000000"), changed("offTime", "20200101100000"),
                changed("offTime", null), changed("lat", "-90000000"), changed("lon", "180000000"),
                changed("sum", "2147483647"), changed("ang", "365"))) {
            PowerLogRequestValidator.validate(request, NOW);
        }
    }

    private static PowerLogRequest changed(String field, String value) {
        Map<String, String> fields = new LinkedHashMap<>(Map.ofEntries(
                Map.entry("mdn", "fixture"), Map.entry("tid", "A001"), Map.entry("mid", "6"),
                Map.entry("pv", "5"), Map.entry("did", "1"), Map.entry("onTime", "20200101100000"),
                Map.entry("offTime", ""), Map.entry("gcd", "A"), Map.entry("lat", "37500000"),
                Map.entry("lon", "127000000"), Map.entry("ang", "0"), Map.entry("spd", "0"), Map.entry("sum", "1000")));
        fields.put(field, value);
        return JSON.convertValue(fields, PowerLogRequest.class);
    }
}
