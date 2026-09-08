package org.thisway.support.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;
class MetricsCredentialTest {
    @Test void 미설정이면_거부하고_잘못된_설정은_시작을_중단한다() {
        var request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer " + "a".repeat(64));
        assertThat(new MetricsSecurityConfig.ScrapeCredential("").permits(request)).isFalse();
        assertThatThrownBy(() -> new MetricsSecurityConfig.ScrapeCredential("secret-bad-config"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret-bad-config");
    }
    @Test void 중복헤더와_과대입력은_거부한다() throws Exception {
        String token = "a".repeat(64);
        var verifier = new MetricsSecurityConfig.ScrapeCredential(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))));
        var request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer " + token);
        assertThat(verifier.permits(request)).isTrue();
        request.addHeader("Authorization", "Bearer " + token);
        assertThat(verifier.permits(request)).isFalse();
        var oversized = new MockHttpServletRequest("GET", "/actuator/prometheus");
        oversized.addHeader("Authorization", "Bearer " + "a".repeat(65536));
        assertThat(verifier.permits(oversized)).isFalse();
    }
}
