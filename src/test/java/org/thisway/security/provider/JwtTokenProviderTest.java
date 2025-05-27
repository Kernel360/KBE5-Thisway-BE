package org.thisway.security.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

public class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    private final String secretKey = "this-is-test-string-secret-key-that-is-32-bytes-long";
    private final long VALIDITY_MS = 60 * 60 * 1000;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(secretKey, VALIDITY_MS);
    }

    @Test
    void JWT_AccessToken_생성_및_검증() {
        // given
        String username = "testUser";
        List<String> roles = List.of("ROLE_USER", "ROLE_ADMIN");
        Map<String, Object> claimsMap = Map.of("roles", roles);

        // when
        String token = provider.createAccessToken(username, claimsMap);

        // then
        Claims claims = provider.validateTokenAndGetClaims(token);
        assertThat(claims.getSubject()).isEqualTo(username);

        @SuppressWarnings("unchecked")
        List<String> tokenRoles = claims.get("roles", List.class);

        // tokenRoles 리스트가 roles 리스트와 "같은 원소를 모두 포함"하고 있는지, "순서와 중복은 무시"하고 검사
        assertThat(tokenRoles).containsExactlyInAnyOrderElementsOf(roles);
    }

    @Test
    void 만료된_JWT_AccessToken에_대한_검증_실패() {
        // given: 만료 시간을 0으로 설정한 Provider
        JwtTokenProvider shortLived = new JwtTokenProvider(secretKey, 0L);
        String shortToken = shortLived.createAccessToken("user", Map.of());

        // when: 생성되자 말자 token 만료됨.

        // then
        assertThrows(
                JwtException.class,
                () -> shortLived.validateTokenAndGetClaims(shortToken));
    }

    @Test
    void 토큰이_발급한_시점과_만료_시점_사이의_간격이_설정한_유효기간과_일치확인() {
        // given
        Instant before = Instant.now();
        String token = provider.createAccessToken("u", Map.of());
        Claims c = provider.validateTokenAndGetClaims(token);

        // when
        Instant issuedAt = c.getIssuedAt().toInstant();
        Instant expiresAt = c.getExpiration().toInstant();
        Duration actual = Duration.between(issuedAt, expiresAt);

        // then
        // 유효기간이 1시간으로 설정되어 있으므로, 1시간(3600초)과 비교
        Duration expected = Duration.ofMillis(VALIDITY_MS);
        assertThat(actual)
                .isCloseTo(expected, Duration.ofSeconds(1L));

        // 발급 시각 타이밍 검증
        assertThat(issuedAt)
                .isBetween(before.minusSeconds(1),
                        Instant.now().plusSeconds(1));
    }

    @Test
    void 서명이_변조된_토큰_검증시_JwtException_발생() {
        // given: 정상 토큰 생성
        String token = provider.createAccessToken("user", Map.of("foo", "bar"));

        // 토큰은 헤더.페이로드.서명 3파트로 구성되어 있음
        String[] parts = token.split("\\.");
        String header = parts[0];
        String payload = parts[1];
        String signature = parts[2];

        // when: 서명 부분만 임의로 변경
        String tamperedSignature = signature + "xyz";
        String tamperedToken = header + "." + payload + "." + tamperedSignature;

        // then: 서명 불일치로 JwtException 발생
        assertThrows(JwtException.class,
                () -> provider.validateTokenAndGetClaims(tamperedToken));
    }

    @Test
    void 서로_다른_서명키로_발급된_토큰_검증시_JwtException_발생() {
        // given: 두 개의 프로바이더, 서로 다른 시크릿
        // JwtTokenProvider p1 = new
        // JwtTokenProvider("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", VALIDITY_MS);
        JwtTokenProvider p1 = new JwtTokenProvider("A".repeat(32), VALIDITY_MS);

        JwtTokenProvider p2 = new JwtTokenProvider("B".repeat(32), VALIDITY_MS);

        String token = p1.createAccessToken("alice", Map.of("foo", "bar"));

        // then: p2로 파싱 시 서명 불일치
        assertThrows(JwtException.class,
                () -> p2.validateTokenAndGetClaims(token));
    }

    @Test
    void 토큰이_null_또는_빈값이면_IllegalArgument예외_발생() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> provider.validateTokenAndGetClaims(null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> provider.validateTokenAndGetClaims("")));
    }

    @Test
    void 형식이_올바르지_않은_토큰_검증시_JwtException_발생() {
        // given: 마침표가 2개가 아닌 잘못된 형식
        String badFormatToken = "invalid.token";
        // given: 올바르지 않은 토큰
        String badToken = "invalid.token.value";

        // when / then
        assertThrows(JwtException.class,
                () -> provider.validateTokenAndGetClaims(badFormatToken));
        assertThrows(JwtException.class,
                () -> provider.validateTokenAndGetClaims(badToken));
    }
}
