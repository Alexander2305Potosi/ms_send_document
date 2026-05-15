package com.example.fileprocessor.domain.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Utility class for handling exception-related operations.
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
        // Utility class
    }

    /**
     * Converts a Throwable's stack trace into a String for logging or persistence.
     *
     * @param throwable the error to convert.
     * @return the full stack trace as a String.
     */
    public static String getStackTraceAsString(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
