package com.company.bds.testsupport;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns the lifecycle of the PostgreSQL databases used by integration tests on the shared test server.
 *
 * <p>{@link #shared()} creates one fresh database per JVM ({@code bds_it_<epochSeconds>_<random>}) that every Spring
 * test context of the run points at; it is dropped by a shutdown hook. Databases left behind by a JVM that was killed
 * are swept on the next run once they are older than {@link #STALE_AFTER} and have no open connection.
 */
public final class BdsTestDatabase {
    public static final String PG_URL = "BDS_TEST_PG_URL";
    public static final String PG_USER = "BDS_TEST_PG_USER";
    public static final String PG_PASSWORD = "BDS_TEST_PG_PASSWORD";

    private static final String PREFIX = "bds_it_";
    private static final Pattern NAME = Pattern.compile("^bds_it_(?:[a-z0-9]+_)?(\\d{10})_[0-9a-f]{8}$");
    private static final Duration STALE_AFTER = Duration.ofHours(12);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Object LOCK = new Object();
    private static volatile Database shared;

    private BdsTestDatabase() {}

    public record Database(String name, String url, String username, String password) {
        public Connection connect() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
    }

    /** The database shared by every Spring context of this JVM, created on first use. */
    public static Database shared() {
        Database current = shared;
        if (current != null) return current;
        synchronized (LOCK) {
            if (shared == null) {
                sweepStaleDatabases();
                Database created = create("");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> drop(created), "bds-test-database-drop"));
                shared = created;
            }
            return shared;
        }
    }

    /** A separate empty database (for example to migrate from an older schema version); the caller must {@link #drop} it. */
    public static Database createScratch(String purpose) {
        if (!purpose.matches("[a-z0-9]{1,20}")) throw new IllegalArgumentException("purpose must be [a-z0-9]{1,20}");
        return create(purpose + "_");
    }

    public static void drop(Database database) {
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + database.name() + " WITH (FORCE)");
        } catch (SQLException ex) {
            System.err.println("Could not drop test database " + database.name() + ": " + ex.getMessage());
        }
    }

    private static Database create(String infix) {
        String name = PREFIX + infix + Instant.now().getEpochSecond() + "_" + HexFormat.of().formatHex(randomBytes(4));
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not create test database " + name + " on " + BdsTestEnvironment.require(PG_URL)
                    + ": " + ex.getMessage(), ex);
        }
        return new Database(name, withDatabase(BdsTestEnvironment.require(PG_URL), name), user(), password());
    }

    private static void sweepStaleDatabases() {
        long cutoff = Instant.now().minus(STALE_AFTER).getEpochSecond();
        try (Connection connection = admin(); Statement statement = connection.createStatement()) {
            List<String> stale = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery("SELECT datname FROM pg_database WHERE datname LIKE 'bds\\_it\\_%'")) {
                while (rows.next()) {
                    Matcher matcher = NAME.matcher(rows.getString(1));
                    if (matcher.matches() && Long.parseLong(matcher.group(1)) < cutoff) stale.add(rows.getString(1));
                }
            }
            for (String name : stale) {
                try {
                    // No FORCE: a database that still has a connection belongs to a live run and is left alone.
                    statement.execute("DROP DATABASE IF EXISTS " + name);
                } catch (SQLException ignored) {
                    // Still in use; retried by a later run.
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot reach the PostgreSQL test server at " + BdsTestEnvironment.require(PG_URL)
                    + " (" + ex.getMessage() + "). Is the shared test infrastructure running? (scripts/test-infra.sh status)", ex);
        }
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(BdsTestEnvironment.require(PG_URL), user(), password());
    }

    private static String user() { return BdsTestEnvironment.require(PG_USER); }

    private static String password() { return BdsTestEnvironment.require(PG_PASSWORD); }

    static String withDatabase(String adminUrl, String database) {
        int hostStart = adminUrl.indexOf("//");
        if (!adminUrl.startsWith("jdbc:postgresql:") || hostStart < 0) {
            throw new IllegalStateException(PG_URL + " must look like jdbc:postgresql://host:port/database");
        }
        int pathStart = adminUrl.indexOf('/', hostStart + 2);
        if (pathStart < 0) return adminUrl + "/" + database;
        int query = adminUrl.indexOf('?', pathStart);
        return adminUrl.substring(0, pathStart + 1) + database + (query < 0 ? "" : adminUrl.substring(query));
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
