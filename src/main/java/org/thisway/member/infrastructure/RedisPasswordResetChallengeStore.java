package org.thisway.member.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.thisway.member.application.PasswordResetChallengeStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Atomic issuance budget and compare-and-consume across application instances. */
@Component
@RequiredArgsConstructor
public class RedisPasswordResetChallengeStore implements PasswordResetChallengeStore {
    private final StringRedisTemplate redis;

    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then return 2 end
            local used = tonumber(redis.call('GET', KEYS[3]) or '0')
            if used >= 5 then return 2 end
            redis.call('SET', KEYS[2], '1', 'PX', 60000)
            local count = redis.call('INCR', KEYS[3])
            if count == 1 then redis.call('PEXPIRE', KEYS[3], 3600000) end
            redis.call('HSET', KEYS[1], 'issuanceId', ARGV[1], 'codeHash', ARGV[2],
                'emailHash', ARGV[3], 'attempts', '0')
            redis.call('PEXPIRE', KEYS[1], 600000)
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'emailHash') ~= ARGV[2] then return 0 end
            local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts') or '0')
            if attempts >= 5 then return 2 end
            if redis.call('HGET', KEYS[1], 'codeHash') == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 1
            end
            attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= 5 then return 2 end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> CANCEL = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'issuanceId') == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    @Override
    public IssueResult issue(long memberId, String canonicalEmail, String issuanceId, String code) {
        String key = key(memberId);
        Long result = redis.execute(ISSUE, List.of(key + ":challenge", key + ":cooldown", key + ":budget"),
                issuanceId, hash(code), hash(canonicalEmail));
        if (Long.valueOf(1).equals(result)) return IssueResult.ISSUED;
        if (Long.valueOf(2).equals(result)) return IssueResult.RATE_LIMITED;
        throw new IllegalStateException("Password reset issuance was not acknowledged");
    }

    @Override
    public ConsumeResult consume(long memberId, String canonicalEmail, String code) {
        Long result = redis.execute(CONSUME, List.of(key(memberId) + ":challenge"), hash(code), hash(canonicalEmail));
        if (Long.valueOf(1).equals(result)) return ConsumeResult.CONSUMED;
        if (Long.valueOf(0).equals(result)) return ConsumeResult.INVALID;
        if (Long.valueOf(2).equals(result)) return ConsumeResult.RATE_LIMITED;
        throw new IllegalStateException("Password reset consumption was not acknowledged");
    }

    @Override
    public void cancel(long memberId, String issuanceId) {
        Long result = redis.execute(CANCEL, List.of(key(memberId) + ":challenge"), issuanceId);
        if (result == null) throw new IllegalStateException("Password reset cancellation was not acknowledged");
    }

    private static String key(long memberId) {
        if (memberId <= 0) throw new IllegalArgumentException("Invalid password reset account");
        // All keys for an account occupy one Redis Cluster hash slot.
        return "thisway:password-reset:{" + memberId + "}";
    }

    private static String hash(String value) {
        if (value == null) throw new IllegalArgumentException("Missing password reset value");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
