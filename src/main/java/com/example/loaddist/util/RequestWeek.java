package com.example.loaddist.util;

import java.time.LocalDate;

public final class RequestWeek {

    private RequestWeek() {}

    public static LocalDate parseWeekParam(String weekParam) {
        if (weekParam == null || weekParam.isBlank()) {
            return Weeks.mondayUtcToday();
        }
        try {
            return Weeks.mondayOf(LocalDate.parse(weekParam.trim()));
        } catch (Exception e) {
            return Weeks.mondayUtcToday();
        }
    }
}
