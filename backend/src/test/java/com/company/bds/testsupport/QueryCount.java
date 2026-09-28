package com.company.bds.testsupport;

import net.ttddyy.dsproxy.QueryCountHolder;

/**
 * Counts the JDBC statements executed on the current thread through the application DataSource (datasource-proxy).
 * MockMvc requests run on the calling thread, so a whole request can be measured:
 * <pre>{@code QueryCount.assertAtMost(4, () -> mockMvc.perform(get("/api/v2/listings/search")));}</pre>
 */
public final class QueryCount {
    static final String DATA_SOURCE_NAME = "bds-test";

    private QueryCount() {}

    @FunctionalInterface
    public interface ThrowingRunnable { void run() throws Exception; }

    @FunctionalInterface
    public interface ThrowingSupplier<T> { T get() throws Exception; }

    /** Number of statements (select/insert/update/delete/other) the action executed. */
    public static long count(ThrowingRunnable action) {
        return measure(() -> { action.run(); return null; }).statements();
    }

    public static void assertAtMost(long maxStatements, ThrowingRunnable action) {
        assertAtMost(maxStatements, () -> { action.run(); return null; });
    }

    public static <T> T assertAtMost(long maxStatements, ThrowingSupplier<T> action) {
        Measured<T> measured = measure(action);
        if (measured.statements() > maxStatements) {
            throw new AssertionError("Expected at most " + maxStatements + " SQL statements but " + measured.statements()
                    + " were executed (" + measured.breakdown() + ")");
        }
        return measured.result();
    }

    private static <T> Measured<T> measure(ThrowingSupplier<T> action) {
        QueryCountHolder.clear();
        try {
            T result = action.get();
            var total = QueryCountHolder.getGrandTotal();
            String breakdown = "select=" + total.getSelect() + ", insert=" + total.getInsert() + ", update=" + total.getUpdate()
                    + ", delete=" + total.getDelete() + ", other=" + total.getOther();
            return new Measured<>(result, total.getTotal(), breakdown);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            QueryCountHolder.clear();
        }
    }

    private record Measured<T>(T result, long statements, String breakdown) {}
}
