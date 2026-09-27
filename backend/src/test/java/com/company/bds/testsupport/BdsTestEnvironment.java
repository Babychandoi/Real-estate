package com.company.bds.testsupport;

/**
 * Reads the shared test infrastructure settings ({@code eval "$(scripts/test-infra.sh env)"} locally, service
 * containers in CI). A system property with the same name wins over the environment variable.
 */
public final class BdsTestEnvironment {
    private BdsTestEnvironment() {}

    public static String require(String name) {
        String value = optional(name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set. Backend integration tests run on the shared PostgreSQL/PostGIS "
                    + "test server: start it with 'scripts/test-infra.sh up' and export its variables with "
                    + "'eval \"$(scripts/test-infra.sh env)\"' (CI provides them through service containers).");
        }
        return value;
    }

    public static String optional(String name, String fallback) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
