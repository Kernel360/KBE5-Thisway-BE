package org.thisway.support.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.prometheus.enabled=true",
        "management.prometheus.metrics.export.enabled=true",
        "management.endpoint.health.show-details=never",
        "management.health.rabbit.enabled=false",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class ActuatorApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health만_인증없이_조회하고_component_상세는_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void prometheus는_인증없이_조회할수있다() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    void health_하위경로는_인증없이_조회할수없다() throws Exception {
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void health의_GET_외_method는_인증없이_호출할수없다() throws Exception {
        mockMvc.perform(post("/actuator/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void allowlist에_없는_actuator_endpoint는_인증되어도_생성되지_않는다() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }
}
