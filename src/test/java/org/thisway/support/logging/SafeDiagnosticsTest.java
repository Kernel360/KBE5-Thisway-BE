package org.thisway.support.logging;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SafeDiagnosticsTest {
    @Test void 원인과_메시지를_버리고_코드_위치를_보존한다() {
        var failure = new IllegalArgumentException("secret-gps", new RuntimeException("secret-key"));
        assertThat(SafeDiagnostics.describe(failure)).contains("IllegalArgumentException@org.thisway.")
                .doesNotContain("secret-gps", "secret-key");
    }
    @Test void 외부_추적값을_검증하고_기존_MDC를_복원한다() {
        org.slf4j.MDC.put("traceId", "outer");
        try {
            try (var scope = TraceContext.open("credential\nforged")) {
                assertThat(org.slf4j.MDC.get("traceId")).matches("[0-9a-f]{32}");
            }
            assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("outer");
        } finally { org.slf4j.MDC.clear(); }
    }
}
