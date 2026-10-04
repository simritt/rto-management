package com.rto.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** All timestamps are naive UTC (the JDBC session also runs in UTC), truncated to whole seconds like DATETIME(0). */
public final class Clock {
    private Clock() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
