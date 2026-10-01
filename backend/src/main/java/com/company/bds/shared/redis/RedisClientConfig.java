package com.company.bds.shared.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.resource.Delay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.ClientResourcesBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Lettuce settings so that a Redis outage fails fast instead of holding request threads.
 *
 * <ul>
 *   <li>Commands issued while the connection is down are rejected at once. Lettuce's default buffers them until the
 *   connection comes back or the command timeout expires, so during an outage every caller waited the full timeout.</li>
 *   <li>Reconnect attempts back off to at most {@code app.redis.reconnect-max-delay} (Lettuce's default grows to 30 s,
 *   so the client could stay disconnected for up to 30 s after Redis was back).</li>
 * </ul>
 * The command and connect timeouts are {@code spring.data.redis.timeout} / {@code connect-timeout} (application.yml);
 * {@link RedisCircuitBreaker} keeps callers away from Redis between probes.
 */
@Configuration(proxyBeanMethods = false)
public class RedisClientConfig {

    @Bean
    LettuceClientOptionsBuilderCustomizer redisFailFastWhileDisconnected() {
        return options -> options.autoReconnect(true).disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }

    @Bean
    ClientResourcesBuilderCustomizer redisReconnectDelay(@Value("${app.redis.reconnect-max-delay:PT2S}") Duration maxDelay) {
        Duration upper = maxDelay.compareTo(Duration.ofMillis(100)) < 0 ? Duration.ofMillis(100) : maxDelay;
        return resources -> resources.reconnectDelay(Delay.exponential(Duration.ofMillis(100), upper, 2, TimeUnit.MILLISECONDS));
    }
}
