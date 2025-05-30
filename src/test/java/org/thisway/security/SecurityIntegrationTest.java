package org.thisway.security;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.security.dto.request.LoginRequest;
import org.thisway.security.service.CustomUserDetailsService;
import org.thisway.security.utils.JwtTokenUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setup() {
        // 로그인 테스트용 모의 유저 설정
        UserDetails user = User.withUsername("user@example.com")
                .password(passwordEncoder.encode("secret"))
                .roles("USER")
                .build();

        given(customUserDetailsService.loadUserByUsername("user@example.com"))
                .willReturn(user);
    }

    @Test
    void 로그인_요청_성공() throws Exception {
        LoginRequest login = new LoginRequest("user@example.com", "secret");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.AUTHORIZATION))
                .andExpect(header().string(HttpHeaders.AUTHORIZATION, startsWith("Bearer ")));
    }

    @Test
    void 인증_토큰_없이_보호된_엔드포인트_접근시_401반환() throws Exception {
        mockMvc.perform(get("/api/members/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 유효한_토큰으로_보호된_엔드포인트_접근시_200반환() throws Exception {
        String token = jwtTokenUtil.createAccessToken("testUser", Map.of("roles", List.of("USER")));

        mockMvc.perform(get("/api/members/1")
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
