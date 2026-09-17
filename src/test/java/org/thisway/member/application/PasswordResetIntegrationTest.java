package org.thisway.member.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.support.component.EmailComponent;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc API filters/service with real MySQL/Redis; outbound email is mocked. BCrypt remains real. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never"
})
@AutoConfigureMockMvc
@DirtiesContext
class PasswordResetIntegrationTest {
    private static final String OLD_PASSWORD = "OriginalPass123!";
    private static final String NEW_PASSWORD = "Replacement123!";

    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "password_reset_test")
            .withEnv("MYSQL_USER", "test")
            .withEnv("MYSQL_PASSWORD", "test")
            .withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/password_reset_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CompanyRepository companies;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @MockitoSpyBean PasswordEncoder passwords;
    @MockitoBean EmailComponent emailComponent;

    private final Map<String, List<String>> sentCodes = new ConcurrentHashMap<>();
    private Company company;
    private Member member;

    @BeforeEach
    void fixture() {
        company = companies.save(Company.builder().name("password reset fixture")
                .crn(UUID.randomUUID().toString()).contact("000").addrRoad("fixture")
                .addrDetail("fixture").memo("disposable fixture").gpsCycle(60).build());
        member = member(email());
        doAnswer(invocation -> {
            String recipient = invocation.getArgument(0);
            Map<String, Object> variables = invocation.getArgument(3);
            sentCodes.computeIfAbsent(recipient, ignored -> new CopyOnWriteArrayList<>())
                    .add((String) variables.get("code"));
            return null;
        }).when(emailComponent).sendMail(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void 즉시_재발송은_429로_거부하고_추가_메일을_보내지_않는다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        send(member.getEmail()).andExpect(status().isTooManyRequests());

        assertThat(sentCodes.get(member.getEmail())).hasSize(1);
        assertOldPassword(member);
    }

    @Test
    void 오입력_5회째부터_정답도_429로_거부한다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String correctCode = latestCode(member.getEmail());
        String wrongCode = correctCode.equals("123456") ? "654321" : "123456";

        for (int attempt = 1; attempt <= 4; attempt++) {
            change(member.getEmail(), wrongCode, NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("13000"));
        }
        change(member.getEmail(), wrongCode, NEW_PASSWORD).andExpect(status().isTooManyRequests());
        change(member.getEmail(), correctCode, NEW_PASSWORD).andExpect(status().isTooManyRequests());
        assertOldPassword(member);
    }

    @Test
    void 동일_코드의_동시_요청은_정확히_하나만_비밀번호를_변경한다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String code = latestCode(member.getEmail());
        CountDownLatch encoding = new CountDownLatch(2);
        // Before atomic consume, force both readers to overlap before either removes the code.
        // After the fix only one may reach BCrypt; the bounded wait then expires normally.
        doAnswer(invocation -> {
            encoding.countDown();
            encoding.await(1, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(passwords).encode(eq(NEW_PASSWORD));

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentChange(start, code));
            var second = executor.submit(() -> concurrentChange(start, code));
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(passwords.matches(NEW_PASSWORD, members.findById(member.getId()).orElseThrow().getPassword()))
                .isTrue();
        change(member.getEmail(), code, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void 이메일을_다른_PK_회원이_재사용하면_이전_코드로_변경할_수_없다() throws Exception {
        String previousEmail = member.getEmail();
        send(previousEmail).andExpect(status().isOk());
        String code = latestCode(previousEmail);
        assertThat(jdbc.update("UPDATE member SET email=? WHERE id=?", email(), member.getId())).isEqualTo(1);
        Member replacement = member(previousEmail);
        assertThat(replacement.getId()).isNotEqualTo(member.getId());

        change(previousEmail, code, NEW_PASSWORD).andExpect(status().isBadRequest());
        assertOldPassword(member);
        assertOldPassword(replacement);
    }

    @Test
    void Redis에는_원문대신_해시와_600초_코드_60초_cooldown_1시간_budget을_저장한다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String code = latestCode(member.getEmail());
        Map<Object, Object> challenge = redis.opsForHash().entries(key("challenge"));

        assertThat(challenge.get("issuanceId")).isNotNull();
        assertThat(challenge.get("codeHash")).isEqualTo(sha256(code));
        assertThat(challenge.get("emailHash")).isEqualTo(sha256(member.getEmail()));
        assertThat(challenge.get("attempts")).isEqualTo("0");
        assertThat(challenge.values()).doesNotContain(code, member.getEmail());
        assertThat(redis.getExpire(key("challenge"), TimeUnit.SECONDS)).isBetween(590L, 600L);
        assertThat(redis.getExpire(key("cooldown"), TimeUnit.SECONDS)).isBetween(50L, 60L);
        assertThat(redis.getExpire(key("budget"), TimeUnit.SECONDS)).isBetween(3590L, 3600L);
    }

    @Test
    void cooldown이_끝나도_첫발송부터_1시간에_5회만_허용하고_다른_회원은_분리한다() throws Exception {
        for (int issue = 1; issue <= 5; issue++) {
            // Advance only this fixture's cooldown; no claim that a real hour elapsed.
            redis.delete(key("cooldown"));
            send(member.getEmail()).andExpect(status().isOk());
        }
        redis.delete(key("cooldown"));
        send(member.getEmail()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("13006"));
        assertThat(sentCodes.get(member.getEmail())).hasSize(5);

        Member independent = member(email());
        send(independent.getEmail()).andExpect(status().isOk());
        // End the fixed window in Redis, then verify a new issuance can begin.
        redis.expire(key("budget"), Duration.ofMillis(20));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(redis.hasKey(key("budget"))).isFalse());
        send(member.getEmail()).andExpect(status().isOk());
        assertThat(sentCodes.get(member.getEmail())).hasSize(6);
    }

    @Test
    void 실제_Redis_TTL이_만료된_코드는_정답이어도_거부한다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String code = latestCode(member.getEmail());
        // Initial ten-minute TTL is asserted separately; shorten this isolated fixture to test expiry.
        redis.expire(key("challenge"), Duration.ofMillis(20));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(redis.hasKey(key("challenge"))).isFalse());

        change(member.getEmail(), code, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("13000"));
        assertOldPassword(member);
    }

    @Test
    void 잘못된_새_비밀번호는_코드를_소비하지_않는다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String code = latestCode(member.getEmail());
        change(member.getEmail(), code, "too-short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("12005"));
        change(member.getEmail(), code, NEW_PASSWORD).andExpect(status().isOk());
        assertThat(passwords.matches(NEW_PASSWORD, members.findById(member.getId()).orElseThrow().getPassword()))
                .isTrue();
    }

    @Test
    void 대소문자와_공백_별칭도_같은_현재회원의_발송한도를_사용한다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        send("  " + member.getEmail().toUpperCase(java.util.Locale.ROOT) + "  ")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("13006"));
        assertThat(sentCodes.get(member.getEmail())).hasSize(1);
    }

    @Test
    void 동일_회원의_동시_발송은_메일을_정확히_한번만_예약한다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return send(member.getEmail()).andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return send(member.getEmail()).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 429);
        }
        assertThat(sentCodes.get(member.getEmail())).hasSize(1);
    }

    @Test
    void 현재_발송의_SMTP실패는_코드를_삭제하고_쿼터는_돌려주지_않는다() throws Exception {
        doThrow(new CustomException(ErrorCode.EMAIL_SEND_ERROR, "fixture-private-smtp-detail"))
                .when(emailComponent).sendMail(anyString(), anyString(), anyString(), anyMap());
        var response = send(member.getEmail()).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("02000")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("fixture-private-smtp-detail", member.getEmail());
        assertThat(redis.hasKey(key("challenge"))).isFalse();
        assertThat(redis.hasKey(key("cooldown"))).isTrue();
        assertThat(redis.hasKey(key("budget"))).isTrue();
        send(member.getEmail()).andExpect(status().isTooManyRequests());
        verify(emailComponent, times(1)).sendMail(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void 이전_SMTP실패의_늦은_cancel은_새_발송의_코드를_삭제하지_않는다() throws Exception {
        CountDownLatch firstMailEntered = new CountDownLatch(1);
        CountDownLatch failFirstMail = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            Map<String, Object> variables = invocation.getArgument(3);
            sentCodes.computeIfAbsent(member.getEmail(), ignored -> new CopyOnWriteArrayList<>())
                    .add((String) variables.get("code"));
            if (calls.incrementAndGet() == 1) {
                firstMailEntered.countDown();
                assertThat(failFirstMail.await(10, TimeUnit.SECONDS)).isTrue();
                throw new CustomException(ErrorCode.EMAIL_SEND_ERROR);
            }
            return null;
        }).when(emailComponent).sendMail(anyString(), anyString(), anyString(), anyMap());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var oldSend = executor.submit(() -> send(member.getEmail()).andReturn().getResponse().getStatus());
            try {
                assertThat(firstMailEntered.await(5, TimeUnit.SECONDS)).isTrue();
                String oldIssuance = (String) redis.opsForHash().get(key("challenge"), "issuanceId");
                // Model a later resend without waiting a minute while the first mocked SMTP call is pending.
                redis.delete(key("cooldown"));
                send(member.getEmail()).andExpect(status().isOk());
                String newIssuance = (String) redis.opsForHash().get(key("challenge"), "issuanceId");
                assertThat(newIssuance).isNotBlank().isNotEqualTo(oldIssuance);
                failFirstMail.countDown();
                assertThat(oldSend.get(10, TimeUnit.SECONDS)).isEqualTo(500);
                assertThat(redis.opsForHash().get(key("challenge"), "issuanceId")).isEqualTo(newIssuance);
                change(member.getEmail(), latestCode(member.getEmail()), NEW_PASSWORD).andExpect(status().isOk());
            } finally {
                failFirstMail.countDown();
            }
        }
    }

    @Test
    void 실제_MySQL_commit실패에도_소비한_코드를_복원하지_않고_새_발송으로_복구한다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String code = latestCode(member.getEmail());
        String constraint = "reset_password_guard_" + member.getId();
        String originalHash = members.findById(member.getId()).orElseThrow().getPassword();
        // CHECK cannot refer to AUTO_INCREMENT id; this generated email uniquely selects the fixture.
        // This disposable database alone rejects its password UPDATE at the SQL boundary.
        jdbc.execute("ALTER TABLE member ADD CONSTRAINT " + constraint + " CHECK (email <> '"
                + member.getEmail() + "' OR password = '" + originalHash + "')");
        try {
            var response = change(member.getEmail(), code, NEW_PASSWORD)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("00000")).andReturn().getResponse();
            assertThat(response.getContentAsString()).doesNotContain(code, member.getEmail(), NEW_PASSWORD, originalHash);
            assertOldPassword(member);
        } finally {
            jdbc.execute("ALTER TABLE member DROP CHECK " + constraint);
        }
        assertThat(redis.hasKey(key("challenge"))).isFalse();
        change(member.getEmail(), code, NEW_PASSWORD).andExpect(status().isBadRequest());
        assertOldPassword(member);
        redis.delete(key("cooldown"));
        send(member.getEmail()).andExpect(status().isOk());
        change(member.getEmail(), latestCode(member.getEmail()), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void Redis_challenge_타입오류는_고정500이며_비밀번호를_변경하지_않는다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String code = latestCode(member.getEmail());
        redis.delete(key("challenge"));
        redis.opsForValue().set(key("challenge"), "fixture-private-redis-detail", Duration.ofMinutes(10));

        var response = change(member.getEmail(), code, NEW_PASSWORD)
                .andExpect(status().isInternalServerError()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("fixture-private-redis-detail", code, member.getEmail());
        assertOldPassword(member);
    }

    @Test
    void 코드소비후_BCrypt중_회사가_비활성화되면_새_DB갱신경계에서_거부한다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String code = latestCode(member.getEmail());
        doAnswer(invocation -> {
            // Commit on a real connection while hashing, before the updater's separate transaction.
            assertThat(jdbc.update("UPDATE company SET active=false WHERE id=?", company.getId())).isEqualTo(1);
            return invocation.callRealMethod();
        }).when(passwords).encode(eq(NEW_PASSWORD));

        change(member.getEmail(), code, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("13000"));
        assertThat(companies.findById(company.getId()).orElseThrow().isActive()).isFalse();
        assertOldPassword(member);
        assertThat(redis.hasKey(key("challenge"))).isFalse();
        assertThat(jdbc.update("UPDATE company SET active=true WHERE id=?", company.getId())).isEqualTo(1);
        change(member.getEmail(), code, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void 코드소비후_BCrypt중_email이_바뀌면_동일_PK라도_옛_소속정보로_갱신하지_않는다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String code = latestCode(member.getEmail());
        String replacementEmail = email();
        doAnswer(invocation -> {
            assertThat(jdbc.update("UPDATE member SET email=? WHERE id=?", replacementEmail, member.getId())).isEqualTo(1);
            return invocation.callRealMethod();
        }).when(passwords).encode(eq(NEW_PASSWORD));

        change(member.getEmail(), code, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("13000"));
        assertThat(members.findById(member.getId()).orElseThrow().getEmail()).isEqualTo(replacementEmail);
        assertOldPassword(member);
        assertThat(redis.hasKey(key("challenge"))).isFalse();
    }

    @Test
    void 비활성_회원은_기존_코드로_변경하거나_새_코드를_발송하지_못한다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String code = latestCode(member.getEmail());
        assertThat(jdbc.update("UPDATE member SET active=false WHERE id=?", member.getId())).isEqualTo(1);

        send(member.getEmail()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("12000"));
        change(member.getEmail(), code, NEW_PASSWORD).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("12000"));
        assertThat(sentCodes.get(member.getEmail())).hasSize(1);
        assertOldPassword(member);
    }

    @Test
    void 회사가_비활성이면_활성_회원도_기존_코드와_새_발송을_사용하지_못한다() throws Exception {
        send(member.getEmail()).andExpect(status().isOk());
        String code = latestCode(member.getEmail());
        assertThat(jdbc.update("UPDATE company SET active=false WHERE id=?", company.getId())).isEqualTo(1);

        send(member.getEmail()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("12000"));
        change(member.getEmail(), code, NEW_PASSWORD).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("12000"));
        assertThat(sentCodes.get(member.getEmail())).hasSize(1);
        assertOldPassword(member);
    }

    private String key(String suffix) {
        return "thisway:password-reset:{" + member.getId() + "}:" + suffix;
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private int concurrentChange(CountDownLatch start, String code) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return change(member.getEmail(), code, NEW_PASSWORD).andReturn().getResponse().getStatus();
    }

    private ResultActions send(String email) throws Exception {
        return mvc.perform(post("/api/auth/verify-code").contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions change(String email, String code, String password) throws Exception {
        return mvc.perform(put("/api/auth/password").contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "code", code, "newPassword", password))));
    }

    private String latestCode(String email) {
        List<String> values = sentCodes.get(email);
        assertThat(values).isNotEmpty();
        return values.getLast();
    }

    private Member member(String email) {
        return members.save(Member.builder().company(company).role(MemberRole.MEMBER)
                .name("fixture").email(email).password(passwords.encode(OLD_PASSWORD))
                .phone("01012345678").memo("disposable fixture").build());
    }

    private void assertOldPassword(Member target) {
        assertThat(passwords.matches(OLD_PASSWORD, members.findById(target.getId()).orElseThrow().getPassword()))
                .isTrue();
    }

    private String email() {
        return "reset-" + UUID.randomUUID() + "@example.test";
    }
}
