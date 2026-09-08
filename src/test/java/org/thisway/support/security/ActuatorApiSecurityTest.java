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

    private static final String TOKEN = java.util.UUID.randomUUID().toString().replace("-", "")
            + java.util.UUID.randomUUID().toString().replace("-", "");
    @org.springframework.test.context.DynamicPropertySource
    static void credential(org.springframework.test.context.DynamicPropertyRegistry properties) {
        properties.add("thisway.metrics.token-sha256", () -> {
            try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(TOKEN.getBytes(java.nio.charset.StandardCharsets.US_ASCII))); }
            catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
        });
    }

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
    void prometheus는_인증없이_조회할수없다() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
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
    @Test
    void 수집키로만_prometheus를_읽을수있다() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + "0".repeat(64)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").param("token", TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles="ADMIN")
    void 사람_ADMIN_인증도_수집키를_대체하지_못한다() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
    }

    @Test
    void 수집키는_업무_API나_쓰기_권한을_주지_않는다() throws Exception {
        mockMvc.perform(get("/api/vehicles").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/actuator/prometheus").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
    }

}
