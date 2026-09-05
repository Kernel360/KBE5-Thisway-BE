package org.thisway.support.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class RedisComponentIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final String PREFIX = "integration-test:";
    private static final String KEY = "payload";
    private static final String REDIS_KEY = PREFIX + KEY;
    private static final long EXPIRATION_MILLIS = 1_000L;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.2-alpine")
    ).withExposedPorts(REDIS_PORT);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisComponent redisComponent;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisComponent = new RedisComponent(redisTemplate, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(REDIS_KEY);
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("실제 Redis에서 JSON round-trip과 TTL 만료가 동작한다")
    void storesRetrievesAndExpiresJsonPayload() {
        TestPayload payload = new TestPayload("123456", 42L);

        redisComponent.storeToRedis(PREFIX, KEY, EXPIRATION_MILLIS, payload);

        assertThat(redisComponent.retrieveFromRedis(PREFIX, KEY, TestPayload.class)).isEqualTo(payload);
        assertThat(redisTemplate.getExpire(REDIS_KEY, TimeUnit.MILLISECONDS)).isPositive();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(
                        redisComponent.retrieveFromRedis(PREFIX, KEY, TestPayload.class)
                ).isNull());
    }

    private record TestPayload(String code, long expiresAt) {
    }
}
