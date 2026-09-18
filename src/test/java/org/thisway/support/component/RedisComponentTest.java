package org.thisway.support.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.component.support.RedisTestConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RedisTestConfig.class)
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class RedisComponentTest {

    private final RedisComponent redisComponent;

    @MockitoSpyBean
    private final StringRedisTemplate redisTemplate;
    @MockitoSpyBean
    private final ObjectMapper objectMapper;

    private final String prefix = "prefix:";
    private final String key = "abc@example.com";
    private final long expiryMillis = 10000;
    private final String data = "data";

    @BeforeEach
    void setUp() {
        redisComponent.delete(prefix, key);
    }

    @Test
    @DisplayName("redis에 성공적으로 저장한다.")
    void givenKeyAndData_whenStoreToRedis_thenStoreCodeInRedis() throws Exception {
        String data = "data";
        redisComponent.storeToRedis(prefix, key, expiryMillis, data);

        String savedData = redisTemplate.opsForValue().get(prefix + key);
        assertThat(savedData).isNotNull();

        String result = objectMapper.readValue(savedData, String.class);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("redis에 저장하던 중 에러 발생 시 server_error 응답을 한다.")
    void givenKeyAndData_whenStoreToRedisAndAnyExceptionThrown_thenReturnServerErrorStatus() throws Exception {
        doThrow(new RuntimeException("redis에 저장 실패")).when(objectMapper).writeValueAsString(any());

        CustomException e = assertThrows(CustomException.class, () -> redisComponent.storeToRedis(prefix, key, expiryMillis, data));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REDIS_STORE_ERROR);
    }

    @Test
    @DisplayName("redis에서 성공적으로 데이터를 추출한다.")
    void givenKey_whenRetrieveFromRedis_thenRetrieveCodeFromRedis() throws Exception {
        redisComponent.storeToRedis(prefix, key, expiryMillis, data);

        String result = redisComponent.retrieveFromRedis(prefix, key, String.class);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(data);
    }

    @Test
    @DisplayName("redis에 요청하는 값이 없으면 Null을 반환한다.")
    void givenKey_whenRetrieveFromRedisAndNoData_thenReturnNull() {
        String result = redisComponent.retrieveFromRedis(prefix, key, String.class);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("redis에서 데이터를 추출하던 중 에러 발생 시 server_error 응답을 한다.")
    void givenKey_whenRetrieveFromRedisAndAnyExceptionThrown_thenReturnErrorStatus() {
        doThrow(new RuntimeException("Redis 에러")).when(redisTemplate).opsForValue();

        CustomException e = assertThrows(CustomException.class, () -> redisComponent.retrieveFromRedis(prefix, key, String.class));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REDIS_RETRIEVE_ERROR);
    }

}
