package dev.jsinco.malts.utility;

import java.sql.SQLException;
import java.util.function.Consumer;

public final class ExceptionUtil {

    @FunctionalInterface
    public interface ThrowingSQLException {
        void run() throws SQLException;
    }

    @FunctionalInterface
    public interface ThrowingSQLExceptionWithReturn<T> {
        T run() throws SQLException;
    }

    public static void runWithSQLExceptionHandling(ThrowingSQLException runnable) {
        try {
            runnable.run();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (Throwable t) {
            throw new RuntimeException("An unexpected error occurred", t);
        }
    }

    // TODO: Better logging
    public static <U> U runWithSQLExceptionHandling(ThrowingSQLExceptionWithReturn<U> supplier) {
        try {
            return supplier.run();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("An unexpected error occurred", t);
        }
    }

    public static void unsafe(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            Text.error("An unexpected error occurred: " + t.getMessage(), t);
        }
    }
}