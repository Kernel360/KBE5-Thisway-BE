package org.thisway.support.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "thisway.metrics.token-sha256=",
        "management.endpoints.web.base-path=/management",
        "management.endpoints.web.exposure.include=prometheus",
        "management.endpoint.prometheus.enabled=true",
        "management.prometheus.metrics.export.enabled=true"})
@AutoConfigureMockMvc
class MetricsUnconfiguredSecurityTest {
    @Autowired MockMvc mvc;
    @Test void 설정이_없으면_변경된_actuator_경로에서도_수집을_거부한다() throws Exception {
        mvc.perform(get("/management/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/management/prometheus").header("Authorization", "Bearer " + "a".repeat(64)))
                .andExpect(status().isUnauthorized());
    }
}
