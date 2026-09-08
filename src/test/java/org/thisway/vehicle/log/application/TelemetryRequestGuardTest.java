package org.thisway.vehicle.log.application;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.thisway.support.common.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class TelemetryRequestGuardTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2-alpine").withExposedPorts(6379);
    static LettuceConnectionFactory connections;
    static StringRedisTemplate redis;
    @BeforeAll static void connect() {
        connections = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connections.afterPropertiesSet(); connections.start();
        redis = new StringRedisTemplate(connections); redis.afterPropertiesSet();
    }
    @AfterAll static void close() { connections.destroy(); }
    @Test void concurrentInstancesAdmitOneAttemptAndKeepFiniteTtl() throws Exception {
        long device = 101; String nonce = UUID.randomUUID().toString();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var start = new CountDownLatch(1);
            List<Future<ErrorCode>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> {
                start.await();
                try { new TelemetryRequestGuard(redis, 120).accept(device, nonce, now()); return null; }
                catch (CustomException rejected) { return rejected.getErrorCode(); }
            }));
            start.countDown(); List<ErrorCode> errors = new ArrayList<>();
            for (var result : results) errors.add(result.get(10, TimeUnit.SECONDS));
            assertThat(errors).filteredOn(Objects::isNull).hasSize(1);
            assertThat(errors).filteredOn(Objects::nonNull).containsOnly(ErrorCode.TELEMETRY_REPLAYED).hasSize(7);
        }
        assertThat(redis.getExpire("telemetry:{device:101}:nonce:" + nonce)).isBetween(590L, 601L);
        assertThat(redis.opsForValue().get("telemetry:{device:101}:rate")).isEqualTo("1");
    }
    @Test void distributedBudgetIsPerDeviceAndRejectedNonceCanRetryAfterWindow() {
        var first = new TelemetryRequestGuard(redis, 2); var second = new TelemetryRequestGuard(redis, 2);
        first.accept(102, UUID.randomUUID().toString(), now()); second.accept(102, UUID.randomUUID().toString(), now());
        String denied = UUID.randomUUID().toString();
        assertError(() -> first.accept(102, denied, now()), ErrorCode.TELEMETRY_RATE_LIMITED);
        assertThat(redis.hasKey("telemetry:{device:102}:nonce:" + denied)).isFalse();
        assertThat(redis.getExpire("telemetry:{device:102}:rate")).isBetween(50L, 60L);
        second.accept(103, denied, now());
        redis.expire("telemetry:{device:102}:rate", Duration.ZERO);
        second.accept(102, denied, now());
    }
    @Test void freshnessUsesRequestClockAndStrictUuidRatherThanEventTime() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1_789_000_000L), ZoneOffset.UTC);
        String nonce = UUID.randomUUID().toString();
        for (int offset : new int[]{-300, 0, 300}) TelemetryRequestGuard.validate(nonce, "" + (1_789_000_000L + offset), clock);
        for (String timestamp : Arrays.asList(null, "", "1789000000.0", "1789000000000", "1788999699", "1789000301"))
            assertError(() -> TelemetryRequestGuard.validate(nonce, timestamp, clock), ErrorCode.INVALID_INPUT_VALUE);
        for (String invalid : Arrays.asList(null, "", "fixture", nonce.toUpperCase(Locale.ROOT), "00000000-0000-1000-8000-000000000000"))
            assertError(() -> TelemetryRequestGuard.validate(invalid, "1789000000", clock), ErrorCode.INVALID_INPUT_VALUE);
    }
    @Test void redisFailureFailsClosedWithoutExposingCommands() {
        var broken = mock(StringRedisTemplate.class);
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("private command"))
                .when(broken).execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class));
        assertError(() -> new TelemetryRequestGuard(broken, 120).accept(1, UUID.randomUUID().toString(), now()), ErrorCode.TELEMETRY_GUARD_UNAVAILABLE);
    }
    private static String now() { return Long.toString(Instant.now().getEpochSecond()); }
    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(code); assertThat(e.getCause()).isNull();
        });
    }
}
