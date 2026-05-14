package com.example.fileprocessor.infrastructure.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Utility for technical date conversions between Domain (Instant) 
 * and Infrastructure (LocalDateTime).
 */
public final class DateMapper {

    private DateMapper() {
        // Utility class
    }

    public static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
