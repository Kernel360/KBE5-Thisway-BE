package org.thisway.support.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisComponentTest {

    private static final String PREFIX = "prefix:";
    private static final String KEY = "abc@example.com";
    private static final String REDIS_KEY = PREFIX + KEY;
    private static final long EXPIRATION_MILLIS = 10_000L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RedisComponent redisComponent;

    @Test
    @DisplayName("직렬화한 값을 millisecond TTL과 함께 Redis에 저장한다")
    void storeToRedisStoresSerializedValueWithExpiration() throws Exception {
        String data = "data";
        String json = "\"data\"";
        when(objectMapper.writeValueAsString(data)).thenReturn(json);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        redisComponent.storeToRedis(PREFIX, KEY, EXPIRATION_MILLIS, data);

        verify(valueOperations).set(REDIS_KEY, json, EXPIRATION_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("Redis 저장 과정의 예외를 REDIS_STORE_ERROR로 변환한다")
    void storeToRedisMapsExceptionToStoreError() throws Exception {
        doThrow(new RuntimeException("serialization failed"))
                .when(objectMapper).writeValueAsString("data");

        CustomException exception = assertThrows(CustomException.class,
                () -> redisComponent.storeToRedis(PREFIX, KEY, EXPIRATION_MILLIS, "data"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REDIS_STORE_ERROR);
    }

    @Test
    @DisplayName("Redis 값을 지정한 타입으로 역직렬화한다")
    void retrieveFromRedisDeserializesStoredValue() throws Exception {
        String json = "\"data\"";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(json);
        when(objectMapper.readValue(json, String.class)).thenReturn("data");

        String result = redisComponent.retrieveFromRedis(PREFIX, KEY, String.class);

        assertThat(result).isEqualTo("data");
    }

    @Test
    @DisplayName("Redis에 값이 없으면 역직렬화하지 않고 null을 반환한다")
    void retrieveFromRedisReturnsNullWhenValueDoesNotExist() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);

        String result = redisComponent.retrieveFromRedis(PREFIX, KEY, String.class);

        assertThat(result).isNull();
        verify(objectMapper, never()).readValue(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Class<Object>>any());
    }

    @Test
    @DisplayName("Redis 조회 과정의 예외를 REDIS_RETRIEVE_ERROR로 변환한다")
    void retrieveFromRedisMapsExceptionToRetrieveError() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenThrow(new RuntimeException("redis unavailable"));

        CustomException exception = assertThrows(CustomException.class,
                () -> redisComponent.retrieveFromRedis(PREFIX, KEY, String.class));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REDIS_RETRIEVE_ERROR);
    }

    @Test
    @DisplayName("삭제 실패는 기존 정책에 따라 호출자에게 전파하지 않는다")
    void deleteIgnoresRedisException() {
        doThrow(new RuntimeException("redis unavailable")).when(redisTemplate).delete(REDIS_KEY);

        assertThatCode(() -> redisComponent.delete(PREFIX, KEY)).doesNotThrowAnyException();
    }
}
