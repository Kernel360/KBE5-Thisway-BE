package org.thisway.support.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.support.component.streaming.SseConnection;
import org.thisway.support.security.utils.JwtTokenProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real login, security filters and MySQL ownership predicates; no mocked authentication or repositories. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never"
})
@AutoConfigureMockMvc
@DirtiesContext
class MemberAccountAdmissionIntegrationTest {
    private static final String PASSWORD = "TestPassword123!";

    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "member_admission_test")
            .withEnv("MYSQL_USER", "test")
            .withEnv("MYSQL_PASSWORD", "test")
            .withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/member_admission_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CompanyRepository companies;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired SseConnection connections;

    private Company company;
    private Company otherCompany;
    private Member member;
    private Member companyChef;
    private Member platformAdmin;
    private String token;

    @BeforeEach
    void fixture() throws Exception {
        company = company();
        otherCompany = company();
        member = member(company, MemberRole.COMPANY_ADMIN, email());
        companyChef = member(company, MemberRole.COMPANY_CHEF, email());
        platformAdmin = member(otherCompany, MemberRole.ADMIN, email());
        token = login(member.getEmail());
    }

    @Test
    void 실제_로그인_토큰은_회원_PK를_포함하고_자기_회사_업무에_접근한다() throws Exception {
        var claims = tokens.validateTokenAndGetClaims(token);
        assertThat(claims.get("memberId", Long.class)).isEqualTo(member.getId());
        assertThat(claims.get("companyId", Long.class)).isEqualTo(company.getId());
        assertThat(claims.getSubject()).isEqualTo(member.getEmail());
        assertDashboardAllowed(token);
    }

    @Test
    void 회원_삭제_API_commit_후_기존_토큰과_새_SSE_구독_및_로그인은_거부한다() throws Exception {
        assertDashboardAllowed(token);
        mvc.perform(delete("/api/company-chef/members/{id}", member.getId())
                        .header(AUTHORIZATION, bearer(login(companyChef.getEmail()))))
                .andExpect(status().isNoContent());
        assertThat(members.findById(member.getId()).orElseThrow().isActive()).isFalse();

        assertDashboardDenied(token);
        mvc.perform(get("/api/vehicles/stream/track").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(connections.getAllKeys()).isEmpty();
        assertLoginDenied(member.getEmail());
    }

    @Test
    void 회사_삭제_API_commit_후_활성_회원도_기존_토큰과_새_로그인을_사용하지_못한다() throws Exception {
        assertDashboardAllowed(token);
        mvc.perform(delete("/api/admin/companies/{id}", company.getId())
                        .header(AUTHORIZATION, bearer(login(platformAdmin.getEmail()))))
                .andExpect(status().isNoContent());
        assertThat(companies.findById(company.getId()).orElseThrow().isActive()).isFalse();
        assertThat(members.findById(member.getId()).orElseThrow().isActive()).isTrue();

        assertDashboardDenied(token);
        assertLoginDenied(member.getEmail());
        assertThat(login(platformAdmin.getEmail())).isNotBlank();
    }

    @Test
    void 이메일_변경_후_이전_토큰은_거부하고_새_이메일의_로그인은_허용한다() throws Exception {
        String replacementEmail = email();
        renameMember(replacementEmail);

        assertDashboardDenied(token);
        assertLoginDenied(member.getEmail());
        String replacement = login(replacementEmail);
        assertThat(tokens.validateTokenAndGetClaims(replacement).get("memberId", Long.class))
                .isEqualTo(member.getId());
        assertDashboardAllowed(replacement);
    }

    @Test
    void 이전_이메일을_동일_회사_동일_역할의_새_회원이_재사용해도_구토큰은_재결합하지_않는다() throws Exception {
        String oldEmail = member.getEmail();
        renameMember(email());
        Member replacementMember = member(company, member.getRole(), oldEmail);
        assertThat(replacementMember.getId()).isNotEqualTo(member.getId());

        assertDashboardDenied(token);
        String replacementToken = login(oldEmail);
        assertThat(tokens.validateTokenAndGetClaims(replacementToken).get("memberId", Long.class))
                .isEqualTo(replacementMember.getId());
        assertDashboardAllowed(replacementToken);
    }

    @Test
    void 현재_DB_역할이_낮아지면_과거_권한_토큰은_거부하고_새_로그인은_현재_역할을_반영한다() throws Exception {
        // No public role mutation API exists. Change only this disposable MySQL fixture.
        assertThat(jdbc.update("UPDATE member SET role=? WHERE id=?", "MEMBER", member.getId())).isEqualTo(1);

        assertDashboardDenied(token);
        String replacement = login(member.getEmail());
        assertThat(tokens.validateTokenAndGetClaims(replacement).get("roles", List.class))
                .containsExactly("MEMBER");
        assertDashboardAllowed(replacement);
    }

    @Test
    void 현재_DB_소속이_변경되면_이전_회사_토큰은_거부하고_새_로그인은_새_소속을_반영한다() throws Exception {
        // No public company reassignment API exists. Change only this disposable MySQL fixture.
        assertThat(jdbc.update("UPDATE member SET company_id=? WHERE id=?", otherCompany.getId(), member.getId()))
                .isEqualTo(1);

        assertDashboardDenied(token);
        String replacement = login(member.getEmail());
        assertThat(tokens.validateTokenAndGetClaims(replacement).get("companyId", Long.class))
                .isEqualTo(otherCompany.getId());
        assertDashboardAllowed(replacement);
    }

    @Test
    void 정상_서명이어도_현재_DB와_다른_회사_claim은_거부한다() throws Exception {
        assertDashboardDenied(signedIdentity(member.getId(), member.getEmail(), otherCompany.getId(), member.getRole()));
        assertDashboardAllowed(token);
    }

    @Test
    void 정상_서명이어도_현재_DB와_다른_권한_claim은_거부한다() throws Exception {
        assertDashboardDenied(signedIdentity(member.getId(), member.getEmail(), company.getId(), MemberRole.MEMBER));
        assertDashboardAllowed(token);
    }

    @Test
    void 존재하지_않는_회원_PK는_같은_이메일_회사_역할이_있어도_거부한다() throws Exception {
        assertDashboardDenied(signedIdentity(Long.MAX_VALUE, member.getEmail(), company.getId(), member.getRole()));
        assertDashboardAllowed(token);
    }

    @Test
    void 다른_활성_회원_PK에_원래_이메일을_붙인_토큰은_거부한다() throws Exception {
        Member otherMember = member(company, member.getRole(), email());
        assertDashboardDenied(signedIdentity(otherMember.getId(), member.getEmail(), company.getId(), member.getRole()));
        assertDashboardAllowed(token);
    }

    @Test
    void 회원_PK가_없는_기존_형식의_토큰은_거부하고_실제_재로그인으로_복구한다() throws Exception {
        String legacy = tokens.generateAccessToken(member.getEmail(), Map.of(
                "roles", List.of(member.getRole().name()), "companyId", company.getId()));
        assertDashboardDenied(legacy);
        assertDashboardAllowed(login(member.getEmail()));
    }

    private void renameMember(String email) throws Exception {
        mvc.perform(put("/api/company-chef/members/{id}", member.getId())
                        .header(AUTHORIZATION, bearer(login(companyChef.getEmail())))
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "admission fixture", "email", email,
                                "phone", "01012345678", "memo", "disposable fixture"))))
                .andExpect(status().isOk());
        assertThat(members.findById(member.getId()).orElseThrow().getEmail()).isEqualTo(email);
    }

    private String login(String email) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        String token = json.readTree(response.getContentAsString()).path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private void assertLoginDenied(String email) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    private void assertDashboardAllowed(String token) throws Exception {
        mvc.perform(get("/api/vehicles/dashboard").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVehicles").value(0));
    }

    private void assertDashboardDenied(String token) throws Exception {
        var response = mvc.perform(get("/api/vehicles/dashboard").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Request could not be processed"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain(token, member.getEmail());
    }

    private String signedIdentity(long memberId, String email, long companyId, MemberRole role) {
        return tokens.generateAccessToken(email, Map.of(
                "memberId", memberId, "roles", List.of(role.name()), "companyId", companyId));
    }

    private Company company() {
        return companies.save(Company.builder().name("admission fixture").crn(UUID.randomUUID().toString())
                .contact("000").addrRoad("fixture").addrDetail("fixture")
                .memo("disposable fixture").gpsCycle(60).build());
    }

    private Member member(Company company, MemberRole role, String email) {
        return members.save(Member.builder().company(company).role(role).name("admission fixture")
                .email(email).password(passwords.encode(PASSWORD)).phone("01012345678")
                .memo("disposable fixture").build());
    }

    private String email() { return UUID.randomUUID() + "@example.test"; }
    private String bearer(String token) { return "Bearer " + token; }
}
