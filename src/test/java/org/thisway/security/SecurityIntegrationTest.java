package org.thisway.security;

import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;
import org.thisway.security.dto.request.LoginRequest;
import org.thisway.security.utils.JwtTokenUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder paosswordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    JwtTokenUtil jwtTokenUtil;

    @BeforeEach
    void setup() {
        memberRepository.deleteAll();
        Member member = Member.builder()
                .name("test")
                .email("user@example.com")
                .password(paosswordEncoder.encode("secret"))
                .phone("01012345678")
                .memo("")
                .build();

        memberRepository.save(member);
    }

    @Test
    void 로그인_요청_성공() throws Exception {
        // given :
        LoginRequest login = new LoginRequest("user@example.com", "secret");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andDo(print())
                .andExpect(status().isOk())
                // Authorization 헤더가 존재하고 "Bearer "로 시작하는지 확인
                .andExpect(header().exists(HttpHeaders.AUTHORIZATION))
                .andExpect(header().string(HttpHeaders.AUTHORIZATION,
                        startsWith("Bearer ")));
    }

    @Test
    void 인증_토큰_없이_보호된_엔드포인트_접근시_401반환() throws Exception {
        mockMvc.perform(get("/api/members/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 유효한_토큰으로_보호된_엔드포인트_접근시_200반환() throws Exception {
        String token = jwtTokenUtil.createAccessToken("testUser", Map.of("roles", List.of("USER")));
        // String token = jwtTokenUtil.createAccessToken("testUser", Map.of());

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
