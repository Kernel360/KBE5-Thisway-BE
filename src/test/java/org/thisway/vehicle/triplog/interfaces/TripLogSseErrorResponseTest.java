package org.thisway.vehicle.triplog.interfaces;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.thisway.support.common.*;
import org.thisway.support.security.dto.request.MemberDetails;
import org.thisway.vehicle.triplog.application.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TripLogSseErrorResponseTest {
    @ParameterizedTest @ValueSource(strings = {"current", "detail"})
    void eventStreamAcceptStillReceivesOriginal404AsJson(String path) throws Exception {
        var streams = mock(StreamCoordinatesService.class);
        var principal = mock(MemberDetails.class); when(principal.getUsername()).thenReturn("fixture");
        when(streams.createStreamForVehicle(7L, "fixture")).thenThrow(new CustomException(ErrorCode.TRIP_LOG_NOT_FOUND));
        when(streams.createStreamForTripLog(7L)).thenThrow(new CustomException(ErrorCode.TRIP_LOG_NOT_FOUND));
        var mvc = MockMvcBuilders.standaloneSetup(new TripLogController(mock(TripLogService.class), streams))
                .setControllerAdvice(new GlobalExceptionHandler()).setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override public boolean supportsParameter(MethodParameter parameter) { return parameter.getParameterType() == MemberDetails.class; }
                    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory factory) { return principal; }
                }).build();
        mvc.perform(get("/api/trip-log/" + path + "/stream/7").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("17000"));
    }
}
