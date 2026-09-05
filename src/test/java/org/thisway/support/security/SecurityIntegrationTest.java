package org.thisway.support.security;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.member.application.AdminMemberService;
import org.thisway.support.security.utils.JwtTokenProvider;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenUtil;

    @MockitoBean
    private AdminMemberService adminMemberService;

    @Test
    void 인증_토큰_없이_보호된_엔드포인트_접근시_401반환() throws Exception {
        mockMvc.perform(delete("/api/admin/members/{id}", 1L))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminMemberService);
    }

    @Test
    void ADMIN_역할과_companyId가_있는_유효한_토큰은_보호된_엔드포인트에_접근한다() throws Exception {
        String token = jwtTokenUtil.generateAccessToken("testUser", Map.of(
                "roles", List.of("ADMIN"),
                "companyId", 1L
        ));

        mockMvc.perform(
                delete("/api/admin/members/{id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(adminMemberService).deleteMember(1L);
    }

    @Test
    void 변조된_토큰으로_보호된_엔드포인트_접근시_401반환() throws Exception {
        String badToken = "Bearer this.is.invalid.token";

        mockMvc.perform(delete("/api/admin/members/{id}", 1L)
                .header(HttpHeaders.AUTHORIZATION, badToken))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminMemberService);
    }
}
