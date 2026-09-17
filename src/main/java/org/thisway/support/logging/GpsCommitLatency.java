package org.thisway.support.logging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Admitted publisher entry to transactional service return; not device event age or broker ack. */
@Component
public class GpsCommitLatency {
    public static final String HEADER = "thisway-admitted-at-ms";
    private final MeterRegistry meters;
    private final ApplicationEventPublisher events;
    public GpsCommitLatency(MeterRegistry meters, ApplicationEventPublisher events) {
        this.meters = meters; this.events = events;
    }
    public record Measurement(String outcome, long millis) { }
    public void committed(Map<String, Object> headers) {
        Object value = headers.get(HEADER);
        String outcome;
        long elapsed = -1;
        if (value == null) outcome = "missing"; // Legacy/replay messages intentionally do not invent ingress time.
        else if (!(value instanceof Long) || (Long) value <= 0) outcome = "invalid";
        else {
            long now = System.currentTimeMillis();
            if ((Long) value > now) outcome = "clock_skew";
            else { outcome = "measured"; elapsed = now - (Long) value; }
        }
        meters.counter("gps.commit.latency.observations", "outcome", outcome).increment();
        if (elapsed >= 0) Timer.builder("gps.admitted.to.commit").publishPercentileHistogram()
                .register(meters).record(elapsed, TimeUnit.MILLISECONDS);
        events.publishEvent(new Measurement(outcome, elapsed));
    }
}
