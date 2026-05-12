package com.example.loaddist.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

public final class Weeks {

    private Weeks() {}

    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Same idea as Node {@code toWeekKey(new Date())} using UTC calendar date. */
    public static LocalDate mondayUtcToday() {
        return mondayOf(LocalDate.now(ZoneOffset.UTC));
    }
}
