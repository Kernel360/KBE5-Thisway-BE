package org.thisway.support.security;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.member.application.AdminMemberService;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.support.security.utils.JwtTokenProvider;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenUtil;

    @MockitoBean
    private AdminMemberService adminMemberService;

    @Autowired private CompanyRepository companies;
    @Autowired private MemberRepository members;
    private Member admin;

    @BeforeEach
    void activeIdentity() {
        Company company = companies.save(Company.builder().name("security fixture").crn("security-identity")
                .contact("000").addrRoad("fixture").addrDetail("fixture").memo("fixture").gpsCycle(60).build());
        admin = members.save(Member.builder().company(company).role(MemberRole.ADMIN).name("security admin")
                .email("security-admin@example.test").password("unused-fixture-password")
                .phone("01000000000").memo("fixture").build());
    }

    @Test
    void 인증_토큰_없이_보호된_엔드포인트_접근시_401반환() throws Exception {
        mockMvc.perform(delete("/api/admin/members/{id}", 1L))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminMemberService);
    }

    @Test
    void ADMIN_역할과_companyId가_있는_유효한_토큰은_보호된_엔드포인트에_접근한다() throws Exception {
        String token = jwtTokenUtil.generateAccessToken(admin.getEmail(), Map.of(
                "roles", List.of("ADMIN"),
                "companyId", admin.getCompany().getId(),
                "memberId", admin.getId()
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidIdentityClaims")
    void 서명이_유효해도_잘못된_주체_권한_소속은_401로_거부한다(
            String scenario, String subject, Map<String, Object> claims) throws Exception {
        // Keep each original malformed claim as the reason for rejection while
        // supplying a real identity for all unrelated claims.
        Map<String, Object> identityClaims = new HashMap<>(claims);
        identityClaims.put("memberId", admin.getId());
        if (Long.valueOf(1L).equals(identityClaims.get("companyId"))) {
            identityClaims.put("companyId", admin.getCompany().getId());
        }
        String token = jwtTokenUtil.generateAccessToken(
                "testUser".equals(subject) ? admin.getEmail() : subject, identityClaims);

        mockMvc.perform(delete("/api/admin/members/{id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Request could not be processed"));

        verifyNoInteractions(adminMemberService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMemberIds")
    void 서명이_유효해도_잘못된_memberId는_401로_거부한다(String scenario, Object memberId) throws Exception {
        Map<String, Object> claims = new HashMap<>(Map.of(
                "roles", List.of("ADMIN"), "companyId", admin.getCompany().getId()));
        if (memberId != null) claims.put("memberId", memberId);
        String token = jwtTokenUtil.generateAccessToken(admin.getEmail(), claims);

        mockMvc.perform(delete("/api/admin/members/{id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Request could not be processed"));
        verifyNoInteractions(adminMemberService);
    }

    private static Stream<Arguments> invalidMemberIds() {
        return Stream.of(
                Arguments.of("missing memberId", null),
                Arguments.of("zero memberId", 0L),
                Arguments.of("negative memberId", -1L),
                Arguments.of("non-numeric memberId", "other"));
    }

    private static Stream<Arguments> invalidIdentityClaims() {
        return Stream.of(
                Arguments.of("missing subject", "", Map.of("roles", List.of("ADMIN"), "companyId", 1L)),
                Arguments.of("blank subject", "  ", Map.of("roles", List.of("ADMIN"), "companyId", 1L)),
                Arguments.of("missing roles", "testUser", Map.of("companyId", 1L)),
                Arguments.of("empty roles", "testUser", Map.of("roles", List.of(), "companyId", 1L)),
                Arguments.of("unknown role", "testUser", Map.of("roles", List.of("UNKNOWN"), "companyId", 1L)),
                Arguments.of("authority instead of domain role", "testUser", Map.of("roles", List.of("ROLE_ADMIN"), "companyId", 1L)),
                Arguments.of("multiple roles", "testUser", Map.of("roles", List.of("MEMBER", "ADMIN"), "companyId", 1L)),
                Arguments.of("non-string role", "testUser", Map.of("roles", List.of(123), "companyId", 1L)),
                Arguments.of("non-list roles", "testUser", Map.of("roles", "ADMIN", "companyId", 1L)),
                Arguments.of("missing company", "testUser", Map.of("roles", List.of("ADMIN"))),
                Arguments.of("zero company", "testUser", Map.of("roles", List.of("ADMIN"), "companyId", 0L)),
                Arguments.of("negative company", "testUser", Map.of("roles", List.of("ADMIN"), "companyId", -1L)),
                Arguments.of("non-numeric company", "testUser", Map.of("roles", List.of("ADMIN"), "companyId", "other"))
        );
    }
}
