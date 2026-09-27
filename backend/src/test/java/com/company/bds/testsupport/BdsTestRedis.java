package com.company.bds.testsupport;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gives each test JVM its own Redis database on the shared test server, so concurrent runs (several agents, or CI jobs on
 * one host) never share rate-limit counters or any other key.
 *
 * <p>Unless {@code BDS_TEST_REDIS_DB} fixes the index, the JVM claims a free database among 16..63 (14..15 when the server
 * has only the default 16; indexes 1..13 belong to the streams, contract §1) with an atomic {@code SET NX EX} on {@value #CLAIM_PREFIX}&lt;index&gt; in database 0; the claim
 * is released when the JVM exits and expires after {@link #CLAIM_TTL} if the JVM is killed. The database is flushed
 * before use.
 */
public final class BdsTestRedis {
    static final String CLAIM_PREFIX = "bds:test:redis-db-claim:";
    static final Duration CLAIM_TTL = Duration.ofHours(6);
    private static final String RUN_ID = "run-" + ProcessHandle.current().pid() + "-" + UUID.randomUUID();
    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    private static final Object LOCK = new Object();
    private static volatile Integer database;

    private BdsTestRedis() {}

    public static String runId() { return RUN_ID; }

    /** The database index of this JVM, claimed and flushed on first use. */
    public static int database() {
        Integer current = database;
        if (current != null) return current;
        synchronized (LOCK) {
            if (database == null) database = claimAndFlush();
            return database;
        }
    }

    /** Owner of the claim on a database index (for tests of this class), or null. */
    public static String claimOwner(int index) {
        return withRedis(redis -> redis.get(CLAIM_PREFIX + index));
    }

    private static int claimAndFlush() {
        String explicit = BdsTestEnvironment.optional("BDS_TEST_REDIS_DB", null);
        int index = explicit != null ? Integer.parseInt(explicit.trim()) : claimFreeDatabase();
        withRedis(redis -> {
            redis.select(index);
            redis.flushdb();
            return null;
        });
        return index;
    }

    private static int claimFreeDatabase() {
        int claimed = withRedis(redis -> {
            int databases = databaseCount(redis);
            List<Integer> candidates = new ArrayList<>();
            int first = databases > 16 ? 16 : 14;
            for (int index = first; index < databases; index++) candidates.add(index);
            Collections.shuffle(candidates);
            for (int index : candidates) {
                if ("OK".equals(redis.set(CLAIM_PREFIX + index, RUN_ID, SetArgs.Builder.nx().ex(CLAIM_TTL.toSeconds())))) return index;
            }
            throw new IllegalStateException("Every test Redis database " + first + ".." + (databases - 1)
                    + " is claimed by another run; wait for them to finish or set BDS_TEST_REDIS_DB.");
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> release(claimed), "bds-test-redis-release"));
        return claimed;
    }

    private static int databaseCount(RedisCommands<String, String> redis) {
        try {
            Map<String, String> config = redis.configGet("databases");
            return Integer.parseInt(config.getOrDefault("databases", "16"));
        } catch (RuntimeException unsupported) {
            return 16;
        }
    }

    private static void release(int index) {
        try {
            withRedis(redis -> redis.eval(RELEASE_SCRIPT, ScriptOutputType.INTEGER, new String[] {CLAIM_PREFIX + index}, RUN_ID));
        } catch (RuntimeException ignored) {
            // the claim expires on its own
        }
    }

    private static <T> T withRedis(java.util.function.Function<RedisCommands<String, String>, T> action) {
        String host = BdsTestEnvironment.optional("BDS_TEST_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(BdsTestEnvironment.optional("BDS_TEST_REDIS_PORT", "56379"));
        RedisClient client = RedisClient.create(RedisURI.builder().withHost(host).withPort(port).withTimeout(Duration.ofSeconds(5)).build());
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            return action.apply(connection.sync());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Cannot reach the test Redis at " + host + ":" + port + " (" + ex.getMessage()
                    + "). Is the shared test infrastructure running? (scripts/test-infra.sh status)", ex);
        } finally {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
        }
    }
}
