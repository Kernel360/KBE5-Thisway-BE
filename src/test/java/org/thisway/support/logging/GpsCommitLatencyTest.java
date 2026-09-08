package org.thisway.support.logging;

import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GpsCommitLatencyTest {
    @Test void 미측정과_시계역전을_지연으로_왜곡하지_않는다() {
        var meters = new SimpleMeterRegistry();
        try {
            var events = mock(ApplicationEventPublisher.class);
            var latency = new GpsCommitLatency(meters, events);
            latency.committed(Map.of());
            latency.committed(Map.of(GpsCommitLatency.HEADER, "123"));
            latency.committed(Map.of(GpsCommitLatency.HEADER, Long.MAX_VALUE));
            assertThat(meters.find("gps.admitted.to.commit").timer()).isNull();
            for (String outcome : new String[]{"missing", "invalid", "clock_skew"})
                assertThat(meters.get("gps.commit.latency.observations").tag("outcome", outcome).counter().count()).isEqualTo(1);
            latency.committed(Map.of(GpsCommitLatency.HEADER, System.currentTimeMillis() - 1000));
            assertThat(meters.get("gps.admitted.to.commit").timer().count()).isEqualTo(1);
            assertThat(meters.get("gps.admitted.to.commit").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(1000);
        } finally { meters.close(); }
    }
}
