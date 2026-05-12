package com.example.loaddist.model;

import java.io.Serializable;
import java.util.Objects;

public class GoalMonthKey implements Serializable {
    private Long goalSlotId;
    private Integer monthIndex;

    public GoalMonthKey() {}

    public GoalMonthKey(Long goalSlotId, Integer monthIndex) {
        this.goalSlotId = goalSlotId;
        this.monthIndex = monthIndex;
    }

    public Long getGoalSlotId() { return goalSlotId; }
    public void setGoalSlotId(Long goalSlotId) { this.goalSlotId = goalSlotId; }

    public Integer getMonthIndex() { return monthIndex; }
    public void setMonthIndex(Integer monthIndex) { this.monthIndex = monthIndex; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GoalMonthKey that = (GoalMonthKey) o;
        return Objects.equals(goalSlotId, that.goalSlotId) && Objects.equals(monthIndex, that.monthIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goalSlotId, monthIndex);
    }
}
