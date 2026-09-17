package org.thisway.support.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.thisway.vehicl_consumer.log.SaveGpsLogConsumer;
import org.thisway.vehicle.log.application.GpsLogSaveService;
import org.thisway.vehicle.log.infrastructure.GpsMessageIdentity;
import org.thisway.emulator.credential.DeviceIdentity;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ConsumerObservabilityTest {
    @Test void 성공과_실패의_지표가_분리되고_추적값이_복원된다() {
        for (boolean fails : List.of(false, true)) {
            var service = mock(GpsLogSaveService.class);
            var request = mock(GpsLogRequest.class); when(request.mdn()).thenReturn("fixture");
            var identity = new DeviceIdentity(1, 2, 3, "fixture", 0);
            var headers = new HashMap<String, Object>(GpsMessageIdentity.headers(identity));
            String trace = "0123456789abcdef0123456789abcdef"; headers.put("traceId", trace);
            doAnswer(call -> {
                assertThat(MDC.get("traceId")).isEqualTo(trace);
                if (fails) throw new IllegalStateException("secret-payload");
                return null;
            }).when(service).saveGpsLog(request, identity);
            var meters = new SimpleMeterRegistry();
            try {
                var latency = mock(GpsCommitLatency.class);
                var consumer = new SaveGpsLogConsumer(service, meters, latency);
                MDC.put("traceId", "outer");
                try {
                    if (fails) assertThatThrownBy(() -> consumer.receiveGpsLog(request, headers)).isInstanceOf(IllegalStateException.class);
                    else consumer.receiveGpsLog(request, headers);
                    verify(latency, times(fails ? 0 : 1)).committed(headers);
                    assertThat(MDC.get("traceId")).isEqualTo("outer");
                    assertThat(meters.get("gps.consumer.processing").tag("outcome", fails ? "failed" : "committed").timer().count()).isEqualTo(1);
                    assertThat(meters.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).hasSize(1));
                } finally { MDC.clear(); }
            } finally { meters.close(); }
        }
    }
}
