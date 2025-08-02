package org.thisway.support.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.support.security.utils.JwtTokenProvider;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenUtil;

    @Test
    void 인증_토큰_없이_보호된_엔드포인트_접근시_401반환() throws Exception {
        mockMvc.perform(get("/api/members/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Disabled
    void 유효한_토큰으로_보호된_엔드포인트_접근시_200반환() throws Exception {
        String token = jwtTokenUtil.generateAccessToken("testUser", Map.of("roles", List.of("USER")));

        mockMvc.perform(
                get("/api/members/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void 변조된_토큰으로_보호된_엔드포인트_접근시_401반환() throws Exception {
        String badToken = "Bearer this.is.invalid.token";

        mockMvc.perform(get("/api/members/1")
                .header(HttpHeaders.AUTHORIZATION, badToken))
                .andExpect(status().isUnauthorized());
    }
}
