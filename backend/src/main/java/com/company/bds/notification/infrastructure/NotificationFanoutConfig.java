package com.company.bds.notification.infrastructure;

import com.company.bds.notification.application.port.NotificationFanoutPort;
import com.company.bds.shared.redis.RedisCircuitBreaker;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * {@code app.notifications.fanout}: {@code redis} (default; required as soon as more than one backend instance runs) or
 * {@code local} (single instance without Redis: streams only get notifications written on the same instance).
 */
@Configuration(proxyBeanMethods = false)
public class NotificationFanoutConfig {

    @Bean
    @ConditionalOnProperty(name = "app.notifications.fanout", havingValue = "redis", matchIfMissing = true)
    RedisNotificationFanout redisNotificationFanout(StringRedisTemplate redis, RedisCircuitBreaker breaker, NotificationSseHub hub,
                                                    ObjectMapper json, MeterRegistry meters) {
        return new RedisNotificationFanout(redis, breaker, hub, json, meters);
    }

    /** Subscribes in the background and re-subscribes with backoff after a Redis outage; never blocks startup. */
    @Bean
    @ConditionalOnProperty(name = "app.notifications.fanout", havingValue = "redis", matchIfMissing = true)
    RedisMessageListenerContainer notificationListenerContainer(RedisConnectionFactory connections,
                                                               RedisNotificationFanout fanout) {
        // Not auto-started: startup must never block or fail when Redis is unreachable (started below, in the background).
        RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
            @Override
            public boolean isAutoStartup() {
                return false;
            }
        };
        container.setConnectionFactory(connections);
        container.addMessageListener(fanout, new ChannelTopic(RedisNotificationFanout.CHANNEL));
        ExponentialBackOff backOff = new ExponentialBackOff(1000, 2.0);
        backOff.setMaxInterval(30_000);
        container.setRecoveryBackoff(backOff);
        return container;
    }

    /** Starts the subscription off the startup thread and keeps retrying until Redis answers. */
    @Bean
    @ConditionalOnProperty(name = "app.notifications.fanout", havingValue = "redis", matchIfMissing = true)
    org.springframework.context.SmartLifecycle notificationSubscriptionStarter(RedisMessageListenerContainer container) {
        return new org.springframework.context.SmartLifecycle() {
            private volatile Thread thread;

            @Override
            public void start() {
                thread = new Thread(() -> {
                    long delay = 1000;
                    while (!Thread.currentThread().isInterrupted() && !container.isRunning()) {
                        try {
                            container.start();
                        } catch (RuntimeException ex) {
                            try {
                                Thread.sleep(delay);
                            } catch (InterruptedException interrupted) {
                                return;
                            }
                            delay = Math.min(30_000, delay * 2);
                        }
                    }
                }, "notification-subscription-starter");
                thread.setDaemon(true);
                thread.start();
            }

            @Override
            public void stop() {
                if (thread != null) thread.interrupt();
                if (container.isRunning()) container.stop();
            }

            @Override
            public boolean isRunning() {
                return thread != null && (thread.isAlive() || container.isRunning());
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.notifications.fanout", havingValue = "local")
    NotificationFanoutPort localNotificationFanout(NotificationSseHub hub) {
        return hub::deliverLocal;
    }
}
