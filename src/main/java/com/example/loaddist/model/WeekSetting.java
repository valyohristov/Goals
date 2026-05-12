package com.example.loaddist.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "ld_week_settings")
public class WeekSetting {

    @Id
    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(nullable = false)
    private boolean manual;

    @Column(name = "copy_to_next_week", nullable = false)
    private boolean copyToNextWeek = true;

    public WeekSetting() {}

    public WeekSetting(LocalDate weekStartDate, boolean manual, boolean copyToNextWeek) {
        this.weekStartDate = weekStartDate;
        this.manual = manual;
        this.copyToNextWeek = copyToNextWeek;
    }

    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }

    public boolean isManual() { return manual; }
    public void setManual(boolean manual) { this.manual = manual; }

    public boolean isCopyToNextWeek() { return copyToNextWeek; }
    public void setCopyToNextWeek(boolean copyToNextWeek) { this.copyToNextWeek = copyToNextWeek; }
}
