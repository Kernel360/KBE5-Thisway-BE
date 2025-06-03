package org.thisway.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisComponent {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public <DATA> void storeToRedis(String prefix, String key, long expirationMills, DATA data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(prefix+key, json, expirationMills, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.SERVER_ERROR);
        }
    }

    public <DATA> DATA retrieveFromRedis(String prefix, String key, Class<DATA> clazz) {
        try {
            String savedCode = redisTemplate.opsForValue().get(prefix+key);

            if (savedCode != null && !savedCode.isBlank()) {
                return objectMapper.readValue(savedCode, clazz);
            } else return null;

        } catch (Exception e) {
            throw new CustomException(ErrorCode.SERVER_ERROR);
        }
    }

    public void delete(String prefix, String key) {
        redisTemplate.delete(prefix+key);
    }

}
