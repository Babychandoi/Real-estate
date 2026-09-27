package com.company.bds.shared.security.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed windows in Redis. One Lua script counts every key of a request atomically: INCR, then PEXPIRE when the key
 * is new or has somehow lost its TTL (so no counter can live forever), then PTTL for an exact {@code Retry-After}.
 * Redis is the only clock involved, so instances with skewed clocks still share one window per key.
 */
final class RedisRateLimitStore {
    private static final String LUA = """
            local out = {}
            for i, key in ipairs(KEYS) do
              local window = tonumber(ARGV[i])
              local count = redis.call('INCR', key)
              local ttl = redis.call('PTTL', key)
              if count == 1 or ttl < 0 then
                redis.call('PEXPIRE', key, window)
                ttl = window
              end
              out[#out + 1] = count
              out[#out + 1] = ttl
            end
            return out
            """;
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>(LUA, List.class);

    private final StringRedisTemplate redis;

    RedisRateLimitStore(StringRedisTemplate redis) { this.redis = redis; }

    List<RateLimitCounter> increment(List<RateLimitKey> keys) {
        List<String> redisKeys = new ArrayList<>(keys.size());
        Object[] windows = new Object[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            redisKeys.add(keys.get(i).redisKey());
            windows[i] = Long.toString(keys.get(i).windowMillis());
        }
        List<?> reply = redis.execute(SCRIPT, redisKeys, windows);
        if (reply == null || reply.size() != keys.size() * 2) {
            throw new IllegalStateException("Redis trả kết quả giới hạn tần suất không hợp lệ");
        }
        List<RateLimitCounter> counters = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            counters.add(RateLimitCounter.counted(toLong(reply.get(2 * i)), Math.max(0, toLong(reply.get(2 * i + 1)))));
        }
        return counters;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof byte[] bytes) return Long.parseLong(new String(bytes, java.nio.charset.StandardCharsets.US_ASCII));
        return Long.parseLong(String.valueOf(value));
    }
}
