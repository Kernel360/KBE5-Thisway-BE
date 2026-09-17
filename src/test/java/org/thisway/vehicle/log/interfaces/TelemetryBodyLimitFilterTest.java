package org.thisway.vehicle.log.interfaces;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class TelemetryBodyLimitFilterTest {
    @Test void actualBytesAreBoundedEvenWithoutContentLength() throws Exception {
        for (boolean chunked : new boolean[]{false, true}) {
            var request = request(chunked, new byte[TelemetryBodyLimitFilter.MAX_BYTES + 1]);
            var response = new MockHttpServletResponse(); var called = new AtomicBoolean();
            new TelemetryBodyLimitFilter(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()).doFilter(request, response, (req, res) -> called.set(true));
            assertThat(response.getStatus()).isEqualTo(413); assertThat(called).isFalse();
            assertThat(response.getContentAsString()).contains("15005");
        }
    }
    @Test void exactLimitAndUtf8BodyRemainReadableByJackson() throws Exception {
        for (byte[] body : new byte[][]{new byte[TelemetryBodyLimitFilter.MAX_BYTES], "{\"tid\":\"서울\"}".getBytes(StandardCharsets.UTF_8)}) {
            var request = request(true, body); var response = new MockHttpServletResponse(); var called = new AtomicBoolean();
            new TelemetryBodyLimitFilter(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()).doFilter(request, response, (req, res) -> {
                called.set(true); assertThat(req.getInputStream().readAllBytes()).isEqualTo(body);
                assertThat(req.getContentLengthLong()).isEqualTo(body.length);
            });
            assertThat(called).isTrue(); assertThat(response.getStatus()).isEqualTo(200);
        }
    }
    @Test void decodedServletPathAlsoReceivesLimit() throws Exception {
        var request = request(true, new byte[TelemetryBodyLimitFilter.MAX_BYTES + 1]);
        request.setRequestURI("/api/logs/%67ps"); request.setServletPath("/api/logs/gps");
        var response = new MockHttpServletResponse();
        new TelemetryBodyLimitFilter(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()).doFilter(request, response, (req,res) -> { throw new AssertionError("oversized route escaped"); });
        assertThat(response.getStatus()).isEqualTo(413);
    }
    private MockHttpServletRequest request(boolean chunked, byte[] body) {
        var request = new MockHttpServletRequest("POST", "/api/logs/gps") {
            @Override public long getContentLengthLong() { return chunked ? -1 : super.getContentLengthLong(); }
        };
        request.setContent(body); return request;
    }
}
