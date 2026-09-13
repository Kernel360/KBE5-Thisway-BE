package org.thisway.member.interfaces;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never", "spring.jpa.show-sql=false", "logging.level.org.thisway=WARN"
})
@AutoConfigureMockMvc
@DirtiesContext
class CompanyMemberWorkflowIntegrationTest {
    private static final String PASSWORD = "TestPassword123!";

    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "member_workflow_test")
            .withEnv("MYSQL_USER", "test")
            .withEnv("MYSQL_PASSWORD", "test")
            .withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/member_workflow_test?allowPublicKeyRetrieval=true&useSSL=false");
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

    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
    private Company company, otherCompany;
    private Member chef, target, foreign;
    private String auth;
    private static final String API = "/api/company-chef/members";

    @BeforeEach void fixture() throws Exception {
        company = company(); otherCompany = company();
        chef = member(company, MemberRole.COMPANY_CHEF, email());
        target = member(company, MemberRole.MEMBER, email());
        foreign = member(otherCompany, MemberRole.MEMBER, email());
        auth = "Bearer " + login(chef.getEmail());
    }

    @Test void roleAndTenantMatrixDoesNotMutateDeniedTargets() throws Exception {
        for (var role : List.of(MemberRole.ADMIN, MemberRole.COMPANY_ADMIN, MemberRole.MEMBER)) {
            String denied = "Bearer " + login(member(company, role, email()).getEmail());
            for (String path : List.of(API, API+"/summary", API+"/"+target.getId()))
                mvc.perform(get(path).header(AUTHORIZATION,denied)).andExpect(status().isForbidden());
            mvc.perform(post(API).header(AUTHORIZATION,denied).contentType(APPLICATION_JSON).content(body(email())))
                    .andExpect(status().isForbidden());
            mvc.perform(put(API+"/"+target.getId()).header(AUTHORIZATION,denied).contentType(APPLICATION_JSON).content(body(email())))
                    .andExpect(status().isForbidden());
            mvc.perform(delete(API+"/"+target.getId()).header(AUTHORIZATION,denied)).andExpect(status().isForbidden());
        }
        mvc.perform(get(API+"/"+foreign.getId()).header(AUTHORIZATION,auth)).andExpect(status().isNotFound());
        mvc.perform(put(API+"/"+foreign.getId()).header(AUTHORIZATION,auth).contentType(APPLICATION_JSON).content(body(email())))
                .andExpect(status().isNotFound());
        mvc.perform(delete(API+"/"+foreign.getId()).header(AUTHORIZATION,auth)).andExpect(status().isNotFound());
        assertThat(members.findById(foreign.getId()).orElseThrow().isActive()).isTrue();
        assertThat(members.findById(target.getId()).orElseThrow().getEmail()).isEqualTo(target.getEmail());
        var list = mvc.perform(get(API).header(AUTHORIZATION,auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(foreign.getEmail());
        mvc.perform(get(API+"/summary").header(AUTHORIZATION,auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.companyChefCount").value(1)).andExpect(jsonPath("$.companyAdminCount").value(1))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test void invalidFieldsAndSortAre4xxAndDatabaseIsUnchanged() throws Exception {
        long before = members.count();
        for (var patch : List.of(Map.of("email","bad"), Map.of("password","password"), Map.of("name"," "),
                Map.of("name","x".repeat(256)),Map.of("memo","x".repeat(256)),Map.of("phone","010x"))) {
            var data = new java.util.HashMap<>(payload(email())); data.putAll(patch);
            mvc.perform(post(API).header(AUTHORIZATION,auth).contentType(APPLICATION_JSON).content(json.writeValueAsString(data)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(API).param("size","101").header(AUTHORIZATION,auth)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("16000"));
        mvc.perform(get(API).param("sort","password,asc").header(AUTHORIZATION,auth)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("16001"));
        assertThat(members.count()).isEqualTo(before);
    }

    @Test void caseOnlySelfUpdateAndCaseInsensitiveDuplicateFollowMysqlCollation() throws Exception {
        mvc.perform(put(API+"/"+target.getId()).header(AUTHORIZATION,auth).contentType(APPLICATION_JSON).content(body(target.getEmail().toUpperCase(java.util.Locale.ROOT))))
                .andExpect(status().isOk());
        mvc.perform(post(API).header(AUTHORIZATION,auth).contentType(APPLICATION_JSON).content(body(target.getEmail())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("12001"));
        mvc.perform(put(API+"/"+target.getId()).header(AUTHORIZATION,auth).contentType(APPLICATION_JSON).content(body(chef.getEmail())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("12001"));
    }

    @Test void concurrentDuplicateRegistrationCommitsExactlyOne() throws Exception {
        String email = email(), body = body(email);
        var start = new java.util.concurrent.CountDownLatch(1);
        try(var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for(int i=0;i<8;i++) tasks.add(pool.submit(()->{start.await();return mvc.perform(post(API).header(AUTHORIZATION,auth)
                    .contentType(APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();}));
            start.countDown();
            var statuses = new java.util.ArrayList<Integer>();
            for(var task:tasks) statuses.add(task.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(statuses).containsOnly(201,400);
            assertThat(statuses.stream().filter(s->s==201).count()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from member where email=?",Long.class,email)).isEqualTo(1);
    }

    @Test void tiedSortAndFilteredCrudUseStableServerResults() throws Exception {
        jdbc.update("update member set name='Same', created_at='2026-09-01 00:00:00' where company_id=?",company.getId());
        var first = mvc.perform(get(API).param("sort","createdAt,desc").param("size","1").param("memberName"," same ").header(AUTHORIZATION,auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pageInfo.totalElements").value(2)).andReturn().getResponse();
        var second = mvc.perform(get(API).param("sort","createdAt,desc").param("size","1").param("page","1").param("memberName","same").header(AUTHORIZATION,auth))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(first.getContentAsString()).at("/members/0/id").asLong()).isEqualTo(chef.getId());
        assertThat(json.readTree(second.getContentAsString()).at("/members/0/id").asLong()).isEqualTo(target.getId());
        mvc.perform(delete(API+"/"+target.getId()).header(AUTHORIZATION,auth)).andExpect(status().isNoContent());
        mvc.perform(get(API).header(AUTHORIZATION,auth)).andExpect(jsonPath("$.pageInfo.totalElements").value(1));
        mvc.perform(get(API+"/summary").header(AUTHORIZATION,auth)).andExpect(jsonPath("$.memberCount").value(0));
    }

    @Test @org.junit.jupiter.api.Tag("fleet-browser")
    void actualBrowserCrudPersistsToMysql() throws Exception {
        var privateFixture = temporary.resolve("members.json");
        java.nio.file.Files.createFile(privateFixture, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
        java.nio.file.Files.writeString(privateFixture,json.writeValueAsString(Map.of("email",chef.getEmail(),"password",PASSWORD)));
        var output = java.nio.file.Path.of("build/reports/company-member-browser").toAbsolutePath();
        java.nio.file.Files.createDirectories(output);
        var builder = new ProcessBuilder("node","tests/live-company-members/workflow.mjs")
                .directory(java.nio.file.Path.of(System.getProperty("fleet.fe.path")).toFile())
                .redirectErrorStream(true).redirectOutput(output.resolve("browser-process.log").toFile());
        builder.environment().put("MEMBERS_FIXTURE_FILE",privateFixture.toString());
        builder.environment().put("MEMBERS_BACKEND_URL","http://127.0.0.1:"+port);
        builder.environment().put("MEMBERS_EVIDENCE_OUTPUT",output.toString());
        var browser = builder.start();
        try {
            assertThat(browser.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(browser.exitValue()).as("See build/reports/company-member-browser/browser-process.log").isZero();
            var saved = members.findAll().stream().filter(m->m.getEmail().equals("member-browser-created@example.test")).findFirst().orElseThrow();
            assertThat(saved.getName()).isEqualTo("브라우저 수정");
            assertThat(saved.isActive()).isFalse();
            assertThat(saved.getCompany().getId()).isEqualTo(company.getId());
        } finally {if(browser.isAlive()) browser.destroyForcibly();java.nio.file.Files.deleteIfExists(privateFixture);}
    }

    private Map<String,String> payload(String email) {return Map.of("role","MEMBER","name","합성 구성원","email",email,"password",PASSWORD,"phone","01012345678","memo","fixture");}
    private String body(String email) throws Exception {return json.writeValueAsString(payload(email));}
    private String login(String email) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email",email,"password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("token").asText();
    }
    private Company company() {return companies.save(Company.builder().name("Synthetic members").crn(UUID.randomUUID().toString()).contact("000").addrRoad("fixture").addrDetail("fixture").memo("disposable").gpsCycle(60).build());}
    private Member member(Company c, MemberRole role, String email) {return members.save(Member.builder().company(c).role(role).name("합성 구성원").email(email).password(passwords.encode(PASSWORD)).phone("01012345678").memo("fixture").build());}
    private String email() {return UUID.randomUUID()+"@example.test";}
}
