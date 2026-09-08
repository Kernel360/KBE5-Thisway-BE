package org.thisway.vehicle.log.application;

import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

/** Per-attempt replay detection and distributed per-device admission budget. */
@Service
public class TelemetryRequestGuard {
    private static final Pattern NONCE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final DefaultRedisScript<Long> ADMIT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return -1 end
            local used = tonumber(redis.call('GET', KEYS[2]) or '0')
            if used >= tonumber(ARGV[1]) then return -2 end
            redis.call('SET', KEYS[1], '1', 'EX', 601)
            local count = redis.call('INCR', KEYS[2])
            if count == 1 then redis.call('EXPIRE', KEYS[2], 60) end
            return count
            """, Long.class);
    private final StringRedisTemplate redis;
    private final int requestsPerMinute;

    public TelemetryRequestGuard(StringRedisTemplate redis,
            @Value("${thisway.telemetry.requests-per-minute:120}") int requestsPerMinute) {
        if (requestsPerMinute < 1) throw new IllegalArgumentException("Positive telemetry budget required");
        this.redis = redis;
        this.requestsPerMinute = requestsPerMinute;
    }

    public void accept(long emulatorId, String nonce, String timestamp) {
        validate(nonce, timestamp, Clock.systemUTC());
        String prefix = "telemetry:{device:" + emulatorId + "}:";
        final Long result;
        try {
            result = redis.execute(ADMIT, List.of(prefix + "nonce:" + nonce, prefix + "rate"),
                    Integer.toString(requestsPerMinute));
        } catch (DataAccessException failure) {
            // Redis exceptions may contain commands; never propagate credential/request data.
            throw new CustomException(ErrorCode.TELEMETRY_GUARD_UNAVAILABLE);
        }
        if (result == null) throw new CustomException(ErrorCode.TELEMETRY_GUARD_UNAVAILABLE);
        if (result == -1) throw new CustomException(ErrorCode.TELEMETRY_REPLAYED);
        if (result == -2) throw new CustomException(ErrorCode.TELEMETRY_RATE_LIMITED);
    }

    static void validate(String nonce, String timestamp, Clock clock) {
        if (nonce == null || !NONCE.matcher(nonce).matches() || timestamp == null
                || !timestamp.matches("[0-9]{10}")
                || Math.abs(clock.instant().getEpochSecond() - Long.parseLong(timestamp)) > 300) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
